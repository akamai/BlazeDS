/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2002 - 2007 Adobe Systems Incorporated
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of Adobe Systems Incorporated and its suppliers,
 * if any.  The intellectual and technical concepts contained
 * herein are proprietary to Adobe Systems Incorporated
 * and its suppliers and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from Adobe Systems Incorporated.
 **************************************************************************/
package flex.messaging.io.amfx;

import flex.messaging.MessageException;
import flex.messaging.io.AbstractProxy;
import flex.messaging.io.ArrayCollection;
import flex.messaging.io.BeanProxy;
import flex.messaging.io.ClassAliasRegistry;
import flex.messaging.io.PropertyProxy;
import flex.messaging.io.PropertyProxyRegistry;
import flex.messaging.io.SerializationContext;
import flex.messaging.io.SerializationException;
import flex.messaging.io.TypeMarshallingContext;
import flex.messaging.io.amf.ASObject;
import flex.messaging.io.amf.AmfTrace;
import flex.messaging.io.amf.ActionMessage;
import flex.messaging.io.amf.MessageHeader;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.io.amf.TraitsInfo;
import flex.messaging.util.ClassUtil;
import flex.messaging.util.Hex;
import flex.messaging.util.XMLUtil;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Context for AMFX specific SAX handler.
 *
 * Contains start and end tag handlers for each of
 * the XML elements that occur in an AMFX request. The
 * AmfxMessageDeserializer enforces a naming convention
 * for these handlers of xyz_start for the start handler
 * and xyz_end for the end handler of element xyz.
 *
 * Note that this context MUST be reset if reused between
 * AMFX packet parsings.
 *
 * @author Peter Farland
 *
 * @see AmfxMessageDeserializer
 * @see AmfxOutput
 */
public class AmfxInput
{
    private SerializationContext context;
    private BeanProxy beanproxy = new BeanProxy();
    
    private XMLStreamReader xmlStreamReader;

    private final ArrayList<Object> objectTable;
    private final ArrayList<String> stringTable;
    private final ArrayList<TraitsInfo> traitsTable;

    private ActionMessage message;
    private MessageHeader currentHeader;
    private MessageBody currentBody;
    private Stack<Object> objectStack;
    private Stack<PropertyProxy> proxyStack;
    private Stack<String> arrayPropertyStack;
    private Stack<int[]> ecmaArrayIndexStack;
    private Stack<int[]> strictArrayIndexStack;
    private Stack<TraitsContext> traitsStack;
    private boolean isStringReference;
    private boolean isTraitProperty;

    /*
     *  DEBUG LOGGING
     */
    protected boolean isDebug;
    protected AmfTrace trace;

    public AmfxInput(SerializationContext context)
    {
        this.context = context;

        stringTable = new ArrayList<String>(64);
        objectTable = new ArrayList<Object>(64);
        traitsTable = new ArrayList<TraitsInfo>(10);

        objectStack = new Stack<Object>();
        proxyStack = new Stack<PropertyProxy>();
        arrayPropertyStack = new Stack<String>();
        strictArrayIndexStack = new Stack<int[]>();
        ecmaArrayIndexStack = new Stack<int[]>();
        traitsStack = new Stack<TraitsContext>();
    }

    public void reset()
    {
        stringTable.clear();
        objectTable.clear();
        traitsTable.clear();
        objectStack.clear();
        proxyStack.clear();
        arrayPropertyStack.clear();
        traitsStack.clear();
        currentBody = null;
        currentHeader = null;

        TypeMarshallingContext marshallingContext = TypeMarshallingContext.getTypeMarshallingContext();
        marshallingContext.reset();
    }

    public void setDebugTrace(AmfTrace trace)
    {
        this.trace = trace;
        isDebug = this.trace != null;
    }

    public void setActionMessage(ActionMessage msg)
    {
        message = msg;
    }
    
    public void setXMLStreamReader(XMLStreamReader xmlStreamReader)
    {
        this.xmlStreamReader = xmlStreamReader;
    }
    
    public Object readObject() throws IOException
    {
        return null;
    }

    public void parse() throws XMLStreamException, IOException
    {
        int eventType = xmlStreamReader.nextTag();
        if (eventType != XMLStreamConstants.START_ELEMENT || !xmlStreamReader.getLocalName().equals("amfx"))
        {
            throw new MessageException("Invalid XML element: " + xmlStreamReader.getLocalName());
        }

        readAMFXElement();
    }
    
    private void readAMFXElement() throws XMLStreamException, IOException
    {
        String ver = xmlStreamReader.getAttributeValue(null, "ver");
        int version = ActionMessage.CURRENT_VERSION;
        if (ver != null)
        {
            try
            {
                version = Integer.parseInt(ver);
            }
            catch (NumberFormatException ex)
            {
                throw new MessageException("Unknown version: " + ver);
            }
        }
  
        if (isDebug)
            trace.version(version);
  
        message.setVersion(version);
        
        // Continue reading the nested XML inside the "amfx" element.
        readElement();
    }
    
    private void readElement() throws XMLStreamException, IOException
    {
        while (xmlStreamReader.nextTag() == XMLStreamConstants.START_ELEMENT)
        {
            String name = xmlStreamReader.getLocalName();
            
            // System.out.println("*** processing element: " + name);

            if (name.equals("array"))
            {
                readArrayElement();
            }
            else if (name.equals("body"))
            {
                readBodyElement();
            }
            else if (name.equals("bytearray"))
            {
                readByteArrayElement();
            }
            else if (name.equals("date"))
            {
                readDateElement();
            }
            else if (name.equals("double"))
            {
                readDoubleElement();
            }
            else if (name.equals("false"))
            {
                readFalseElement();
            }
            else if (name.equals("header"))
            {
                readHeaderElement();
            }
            else if (name.equals("int"))
            {
                readIntElement();
            }
            else if (name.equals("item"))
            {
                readItemElement();
            }
            else if (name.equals("null"))
            {
                readNullElement();
            }
            else if (name.equals("object"))
            {
                readObjectElement();
            }
            else if (name.equals("ref"))
            {
                readRefElement();
            }
            else if (name.equals("string"))
            {
                readStringElement();
            }
            else if (name.equals("traits"))
            {
                readTraitsElement();
            }
            else if (name.equals("true"))
            {
                readTrueElement();
            }
            else if (name.equals("undefined"))
            {
                // Processed the same way as "null".
                readNullElement();
            }
            else if (name.equals("xml"))
            {
                readXmlElement();
            }
            else
            {
                throw new MessageException("Unexpected XML element: " + name);
            }
        }
    }
    
    private void readBodyElement() throws XMLStreamException, IOException
    {
        if (currentBody != null || currentHeader != null)
          throw new MessageException("Unexpected body tag.");
  
        currentBody = new MessageBody();
    
        String targetURI = xmlStreamReader.getAttributeValue(null, "targetURI");
        currentBody.setTargetURI(targetURI);
    
        String responseURI = xmlStreamReader.getAttributeValue(null, "responseURI");
        currentBody.setResponseURI(responseURI);
    
        if (isDebug)
            trace.startMessage(targetURI, responseURI, message.getBodyCount());
        
        // Continue reading the nested XML inside the "body" element.
        readElement();
        
        message.addBody(currentBody);
        currentBody = null;

        if (isDebug)
            trace.endMessage();
    }
    
    private void readHeaderElement() throws XMLStreamException, IOException
    {
        if (currentHeader != null || currentBody != null)
            throw new MessageException("Unexpected header tag.");
  
        currentHeader = new MessageHeader();
    
        String name = xmlStreamReader.getAttributeValue(null, "name");
        currentHeader.setName(name);
    
        String mu = xmlStreamReader.getAttributeValue(null, "mustUnderstand");
        boolean mustUnderstand = false;
        if (mu != null)
        {
            mustUnderstand = Boolean.valueOf(mu).booleanValue();
            currentHeader.setMustUnderstand(mustUnderstand);
        }
    
        if (isDebug)
            trace.startHeader(name, mustUnderstand, message.getHeaderCount());
        
        // Continue reading the nested XML inside the "header" element.
        readElement();
        
        message.addHeader(currentHeader);
        currentHeader = null;

        if (isDebug)
            trace.endHeader();
    }
    
    private void readTraitsElement() throws XMLStreamException, IOException
    {
        if (!objectStack.empty())
        {
            Object obj = objectStack.peek();
            PropertyProxy pp = proxyStack.peek();

            TraitsInfo traitsInfo;
  
            String id = xmlStreamReader.getAttributeValue(null, "id");
            if (id != null)
            {
                try
                {
                    int i = Integer.parseInt(id);
                    traitsInfo = traitsTable.get(i); 

                    TraitsContext traitsContext = new TraitsContext(traitsInfo);
                    traitsStack.push(traitsContext);
                    
                    xmlStreamReader.nextTag();
                }
                catch (NumberFormatException ex)
                {
                    throw new MessageException("Invalid traits reference: " + id);
                }
                catch (IndexOutOfBoundsException ex)
                {
                    throw new MessageException("Unknown traits reference: " + id);
                }
            }
            else
            {
                boolean externalizable = false;
  
                String ext = xmlStreamReader.getAttributeValue(null, "externalizable");
                if (ext != null)
                {
                    externalizable = "true".equals(ext.trim());
                }
  
                traitsInfo = new TraitsInfo(pp.getAlias(obj), pp.isDynamic(), externalizable, 10);
                traitsTable.add(traitsInfo);
                
                TraitsContext traitsContext = new TraitsContext(traitsInfo);
                traitsStack.push(traitsContext);
                
                if (externalizable)
                {
                    xmlStreamReader.nextTag();
                }
                else
                {
                    isTraitProperty = true;
                    
                    // Continue reading the nested XML inside the "traits" element (should contain the actual list of traits).
                    readElement();
                    
                    isTraitProperty = false;
                }
            }

            if (traitsInfo.isExternalizable())
            {
                if (!(obj instanceof Externalizable))
                {
                    //Class '{className}' must implement java.io.Externalizable to receive client IExternalizable instances.
                    SerializationException ex = new SerializationException();
                    ex.setMessage(10305, new Object[] {obj.getClass().getName()});
                    throw ex;
                }

                Externalizable extern = (Externalizable)obj;

                try
                {
                    extern.readExternal(new ExternalizableObjectInput(xmlStreamReader));
                }
                catch (ClassNotFoundException ex)
                {
                    throw new MessageException("Failed to read externalized content.", ex);
                }
            }
        }
        else
        {
            throw new MessageException("Unexpected traits");
        }
    }
    
    private void readStringElement() throws XMLStreamException, IOException
    {
        String id = xmlStreamReader.getAttributeValue(null, "id");
        if (id != null)
        {
            isStringReference = true;
  
            try
            {
                int i = Integer.parseInt(id);
                String s = (String)stringTable.get(i);
                if (isTraitProperty)
                {
                    TraitsContext traitsContext = (TraitsContext)traitsStack.peek();
                    traitsContext.traits.addProperty(s);
                }
                else
                {
                    setValue(s);
                }

                xmlStreamReader.nextTag();
            }
            catch (NumberFormatException ex)
            {
                throw new MessageException("Invalid string reference: " + id);
            }
            catch (IndexOutOfBoundsException ex)
            {
                throw new MessageException("Unknown string reference: " + id);
            }
        }
        else
        {
            isStringReference = false;
        }
        
        if (!isStringReference)
        {
            String s = xmlStreamReader.getElementText();

            // Special case the empty string as it isn't counted as in
            // the string reference table
            if (s.length() > 0)
            {
                // Traits won't contain CDATA
              
                // NOT NEEDED WHEN USING STREAMING XML READER
                // if (!isTraitProperty)
                //     s = unescapeCloseCDATA(s);

                stringTable.add(s);
            }

            if (isTraitProperty)
            {
                TraitsContext traitsContext = (TraitsContext)traitsStack.peek();
                traitsContext.traits.addProperty(s);
            }
            else
            {
                setValue(s);

                if (isDebug)
                    trace.writeString((String)s);
            }
        }
    }
    
    private void readTrueElement() throws XMLStreamException, IOException
    {
        setValue(Boolean.TRUE);
        xmlStreamReader.nextTag();
    }

    private void readFalseElement() throws XMLStreamException, IOException
    {
        setValue(Boolean.FALSE);
        xmlStreamReader.nextTag();
    }

    private void readNullElement() throws XMLStreamException, IOException
    {
        setValue(null);
        xmlStreamReader.nextTag();
    }

    private void readDateElement() throws XMLStreamException, IOException
    {
        String d = xmlStreamReader.getElementText();
        try
        {
            long l = Long.parseLong(d);
            Date date = new Date(l);
            setValue(date);
  
            objectTable.add(date); //Dates can be sent by reference
  
            if (isDebug)
                trace.write(date);
        }
        catch (NumberFormatException ex)
        {
            throw new MessageException("Invalid date: " + d);
        }
    }

    private void readDoubleElement() throws XMLStreamException, IOException
    {
        String ds = xmlStreamReader.getElementText();
        try
        {
            Double d = Double.valueOf(ds);
            setValue(d);
  
            if (isDebug)
                trace.write(d.doubleValue());
        }
        catch (NumberFormatException ex)
        {
            throw new MessageException("Invalid double: " + ds);
        }
    }

    private void readIntElement() throws XMLStreamException, IOException
    {
        String is = xmlStreamReader.getElementText();
        try
        {
            Integer i = Integer.valueOf(is);
            setValue(i);
  
            if (isDebug)
                trace.write(i.intValue());
        }
        catch (NumberFormatException ex)
        {
            throw new MessageException("Invalid int: " + is);
        }
    }

    private Object readArrayElement() throws XMLStreamException, IOException
    {
        int length = 10;
        String len = xmlStreamReader.getAttributeValue(null, "length");
        if (len != null)
        {
            try
            {
                len = len.trim();
                length = Integer.parseInt(len);
                if (length < 0)
                    throw new NumberFormatException();
            }
            catch (NumberFormatException ex)
            {
                throw new MessageException("Invalid array length: " + len);
            }
        }
  
  
        String ecma = xmlStreamReader.getAttributeValue(null, "ecma");
        boolean isECMA = "true".equalsIgnoreCase(ecma);
  
        Object array;
  
        if (isECMA)
        {
            array = new HashMap<Object, Object>();
        }
        else if (context.legacyCollection)
        {
            array = new ArrayList<Object>(length);
        }
        else
        {
            array = new Object[length];
        }
  
        setValue(array);
  
        ecmaArrayIndexStack.push(new int[]{0});
        strictArrayIndexStack.push(new int[]{0});
  
        objectTable.add(array);
        objectStack.push(array);
        proxyStack.push(null);
  
        if (isECMA)
        {
            if (isDebug)
                trace.startECMAArray(objectTable.size() - 1);
        }
        else
        {
            if (isDebug)
                trace.startAMFArray(objectTable.size() - 1);
        }
        
        // Continue reading the nested XML inside the "array" element (should contain the array contents).
        readElement();

        try
        {
            objectStack.pop();
            proxyStack.pop();
            ecmaArrayIndexStack.pop();
            strictArrayIndexStack.pop();
        }
        catch (EmptyStackException ex)
        {
            throw new MessageException("Unexpected end of array");
        }

        if (isDebug)
            trace.endAMFArray();
        
        return array;
    }

    private void readByteArrayElement() throws XMLStreamException, IOException
    {
        String bs = xmlStreamReader.getElementText();
        
        Hex.Decoder decoder = new Hex.Decoder();
        decoder.decode(bs);
        byte[] value = decoder.drain();
  
        setValue(value);
  
        if (isDebug)
            trace.startByteArray(objectTable.size() - 1, bs.length());
    }

    private void readItemElement() throws XMLStreamException, IOException
    {
        String name = xmlStreamReader.getAttributeValue(null, "name");
        if (name != null)
        {
            name = name.trim();
            if (name.length() <= 0)
                throw new MessageException("Array item names cannot be the empty string.");
  
            char c = name.charAt(0);
            if (!(Character.isLetterOrDigit(c) || c == '_'))
                throw new MessageException("Invalid item name: " + name +
                        ". Array item names must start with a letter, a digit or the underscore '_' character.");
        }
        else
        {
            throw new MessageException("Array item must have a name attribute.");
        }
  
        //Check that we're expecting an ECMA array
        Object o = objectStack.peek();
        if (!(o instanceof Map))
        {
            throw new MessageException("Unexpected array item name: " + name +
                    ". Please set the ecma attribute to 'true'.");
        }
  
        arrayPropertyStack.push(name);
        
        // Continue reading the nested XML inside the "item" element (should contain the item's value).
        readElement();

        arrayPropertyStack.pop();
    }

    private Object readObjectElement() throws XMLStreamException, IOException
    {
        PropertyProxy proxy = null;
  
        String type = xmlStreamReader.getAttributeValue(null, "type");
        if (type != null)
        {
            type = type.trim();
        }
  
        Object object;
        
        if (type != null && type.length() > 0)
        {
            // Check for any registered class aliases 
            String aliasedClass = ClassAliasRegistry.getRegistry().getClassName(type);
            if (aliasedClass != null)
                type = aliasedClass;
  
            if (type == null || type.length() == 0)
            {
                object = new ASObject();
            }
            else if (type.startsWith(">")) // Handle [RemoteClass] (no server alias)
            {
                object = new ASObject();
                ((ASObject)object).setType(type);
            }
            else if (context.instantiateTypes || type.startsWith("flex."))
            {
                Class<?> desiredClass = AbstractProxy.getClassFromClassName(type, context.createASObjectForMissingType);
  
                proxy = PropertyProxyRegistry.getRegistry().getProxyAndRegister(desiredClass);
  
                if (proxy == null)
                    object = ClassUtil.createDefaultInstance(desiredClass, null);
                else
                    object = proxy.createInstance(type);
            }
            else
            {
                // Just return type info with an ASObject...
                object = new ASObject();
                ((ASObject)object).setType(type);
            }
        }
        else
        {
            // TODO: QUESTION: Pete, Investigate why setValue for ASObject is delayed to endObject 
            object = new ASObject(type);
        }
  
        if (proxy == null)
            proxy = PropertyProxyRegistry.getProxyAndRegister(object);
  
        objectStack.push(object);
        proxyStack.push(proxy);
        objectTable.add(object);
  
        if (isDebug)
            trace.startAMFObject(type, objectTable.size() - 1);
        
        // Continue reading the nested XML inside the "object" element (should contain the object's content).
        readElement();

        if (!traitsStack.empty())
          traitsStack.pop();
      
        if (!objectStack.empty())
        {
            Object obj = objectStack.pop();
            proxy = (PropertyProxy) proxyStack.pop();
  
            Object newObj = proxy == null ? obj : proxy.instanceComplete(obj);
            if (newObj != obj)
            {
                int i;
                // Find the index in the list of the old objct and replace it with 
                // the new one.
                for (i = 0; i < objectTable.size(); i++)
                    if (objectTable.get(i) == obj)
                        break;
  
                if (i != objectTable.size())
                    objectTable.set(i, newObj);
  
                obj = newObj;
            }
            setValue(obj);
            
            if (isDebug)
                trace.endAMFObject();
            
            return obj;
        }
        else
        {
            throw new MessageException("Unexpected end of object.");
        }
    }

    private void readRefElement() throws XMLStreamException, IOException
    {
        String id = xmlStreamReader.getAttributeValue(null, "id");
        if (id != null)
        {
            try
            {
                int i = Integer.parseInt(id);
                Object o = objectTable.get(i);
                setValue(o);
  
                if (isDebug)
                    trace.writeRef(i);

                xmlStreamReader.nextTag();
            }
            catch (NumberFormatException ex)
            {
                throw new MessageException("Invalid object reference: " + id);
            }
            catch (IndexOutOfBoundsException ex)
            {
                throw new MessageException("Unknown object reference: " + id);
            }
        }
        else
        {
            throw new MessageException("Unknown object reference: " + id);
        }
    }

    private void readXmlElement() throws XMLStreamException, IOException
    {
      String xml = xmlStreamReader.getElementText();
      
      Object value = XMLUtil.stringToDocument(xml, !(context.legacyXMLNamespaces));
      setValue(value);
    }

    @SuppressWarnings("unchecked")
    private void setValue(Object value)
    {
        if (objectStack.empty())
        {
            // Headers
            if (currentHeader != null)
            {
                currentHeader.setData(value);
            }

            // Body
            else if (currentBody  != null)
            {
                currentBody.setData(value);
            }

            else
            {
                throw new MessageException("Unexpected value: " + value);
            }

            return;
        }


        // ActionScript Data
        Object obj = objectStack.peek();

        // <object type="..."> <traits externalizable="true">
        if (obj instanceof Externalizable)
        {
            // Nothing to do.
        }

        // <object>
        else if (obj instanceof ASObject)
        {
            String prop;

            TraitsContext traitsContext = (TraitsContext)traitsStack.peek();
            try
            {
                prop = traitsContext.next();
            }
            catch (IndexOutOfBoundsException ex)
            {
                throw new MessageException("Object has no trait info for value: " + value);
            }

            ASObject aso = (ASObject)obj;
            aso.put(prop, value);

            if (isDebug)
                trace.namedElement(prop);
        }

        // <array ecma="false"> in ArrayList form
        else if (obj instanceof ArrayList && !(obj instanceof ArrayCollection))
        {
            ArrayList<Object> list = (ArrayList<Object>)obj;
            list.add(value);

            if (isDebug)
                trace.arrayElement(list.size() - 1);

        }

        // <array ecma="false"> in Object[] form
        else if (obj.getClass().isArray())
        {
            if (!strictArrayIndexStack.empty())
            {
                int[] indexObj = (int[])strictArrayIndexStack.peek();
                int index = indexObj[0];

                if (Array.getLength(obj) > index)
                {
                    // System.out.println("*** setting array element " + index + " to: " + value);
                    Array.set(obj, index, value);
                }
                else
                {
                    throw new MessageException(xmlStreamReader.getLocation() + " Index out of bounds at: " + index + " cannot set array value: " + value + "");
                }
                indexObj[0]++;
            }
        }

        // <array ecma="true">
        else if (obj instanceof Map)
        {
            Map<Object, Object> map = (Map<Object, Object>)obj;

            // <item name="prop">
            if (!arrayPropertyStack.empty())
            {
                String prop = (String)arrayPropertyStack.peek();
                map.put(prop, value);

                if (isDebug)
                    trace.namedElement(prop);

                return;
            }

            // Mixed content, auto-generate string for ECMA Array index
            if (!ecmaArrayIndexStack.empty())
            {
                int[] index = (int[])ecmaArrayIndexStack.peek();

                String prop = String.valueOf(index[0]);
                index[0]++;

                map.put(prop, value);

                if (isDebug)
                    trace.namedElement(prop);
            }
        }

        // <object type="...">
        else
        {
            String prop;

            TraitsContext traitsContext = (TraitsContext)traitsStack.peek();
            try
            {
                prop = traitsContext.next();
            }
            catch (IndexOutOfBoundsException ex)
            {
                throw new MessageException("Object has no trait info for value: " + value, ex);
            }

            try
            {
                // Then check if there's a more suitable proxy now that we have an instance
                PropertyProxy proxy = (PropertyProxy) proxyStack.peek();
                if (proxy == null)
                    proxy = beanproxy;
                proxy.setValue(obj, prop, value);
            }
            catch (Exception ex)
            {
                throw new MessageException("Failed to set property '" + prop + "' with value: " + value, ex);
            }


            if (isDebug)
                trace.namedElement(prop);
        }
    }

    private class TraitsContext
    {
        private TraitsInfo traits;
        private int counter;

        private TraitsContext(TraitsInfo traits)
        {
            this.traits = traits;
        }

        private String next()
        {
            String trait = (String)traits.getProperties().get(counter);
            counter++;
            return trait;
        }
    }
    
    private class ExternalizableObjectInput implements ObjectInput
    {
        private XMLStreamReader xmlStreamReader;
        
        private String charBuffer;
        private int charBufferPos;

        public ExternalizableObjectInput(XMLStreamReader xmlStreamReader)
        {
            this.xmlStreamReader = xmlStreamReader;
        }
  
        @Override
        public boolean readBoolean() throws IOException
        {
            try
            {
                xmlStreamReader.nextTag();
                
                String name = xmlStreamReader.getLocalName();

                if (name.equals("true"))
                {
                    xmlStreamReader.nextTag();
                    return true;
                }
                else if (name.equals("false"))
                {
                    xmlStreamReader.nextTag();
                    return false;
                }
                else
                {
                    throw new MessageException("Expected boolean element but found \"" + name + "\" (at " + xmlStreamReader.getLocation() + ")");
                }
            }
            catch (XMLStreamException ex)
            {
                throw new MessageException("Failed to read input XML.", ex);
            }
        }
  
        @Override
        public byte readByte() throws IOException
        {
            return (byte)read();
        }
  
        @Override
        public int readUnsignedByte() throws IOException
        {
            return read();
        }
  
        @Override
        public short readShort() throws IOException
        {
            return Short.parseShort(readElementText("short"));
        }

        @Override
        public int readUnsignedShort() throws IOException
        {
            return Integer.parseInt(readElementText("short"));
        }
  
        @Override
        public char readChar() throws IOException
        {
            if (charBuffer == null)
            {
                try
                {
                    xmlStreamReader.nextTag();
                    
                    String name = xmlStreamReader.getLocalName();
      
                    if (name.equals("char") || name.equals("chars"))
                    {
                        charBuffer = xmlStreamReader.getElementText();
                        charBufferPos = 0;
                    }
                    else
                    {
                        throw new MessageException("Expected \"char\" or \"chars\" element but found \"" + name + "\" (at " + xmlStreamReader.getLocation() + ")");
                    }
                }
                catch (XMLStreamException ex)
                {
                    throw new MessageException("Failed to read input XML.", ex);
                }
            }

            char c = charBuffer.charAt(charBufferPos++);
            
            if (charBufferPos == charBuffer.length())
            {
                charBuffer = null;
            }
            
            return c;
        }
  
        @Override
        public int readInt() throws IOException
        {
            return Integer.parseInt(readElementText("int"));
        }
  
        @Override
        public long readLong() throws IOException
        {
            return Long.parseLong(readElementText("long"));
        }
  
        @Override
        public float readFloat() throws IOException
        {
            return Float.parseFloat(readElementText("float"));
        }
  
        @Override
        public double readDouble() throws IOException
        {
            return Double.parseDouble(readElementText("double"));
        }
  
        @Override
        public String readUTF() throws IOException
        {
            return readElementText("string");
        }
  
        @Override
        public Object readObject() throws ClassNotFoundException, IOException
        {
          try
          {
              xmlStreamReader.nextTag();
              
              String name = xmlStreamReader.getLocalName();
              
              if (name.equals("string"))
              {
                  return xmlStreamReader.getElementText();
              }
              else if (name.equals("array"))
              {
                  return AmfxInput.this.readArrayElement();
              }
              else if (name.equals("object"))
              {
                  return AmfxInput.this.readObjectElement();
              }
              else
              {
                  throw new MessageException("Expected \"object\" or \"string\" element but found \"" + name + "\" (at " + xmlStreamReader.getLocation() + ")");
              }
          }
          catch (XMLStreamException ex)
          {
              throw new MessageException("Failed to read input XML.", ex);
          }
        }
  
        @Override
        public int read() throws IOException
        {
            return Integer.parseInt(readElementText("byte"), 16);
        }
  
        @Override
        public int read(byte[] b) throws IOException
        {
            String hex = readElementText("bytes");
            
            Hex.Decoder decoder = new Hex.Decoder();
            decoder.decode(hex);
            byte[] decoded = decoder.drain();
            
            int length = Math.min(b.length, decoded.length);
            
            System.arraycopy(decoded, 0, b, 0, length);
            return length;
        }
  
        @Override
        public int read(byte[] b, int off, int len) throws IOException
        {
            String hex = readElementText("bytes");
            
            Hex.Decoder decoder = new Hex.Decoder();
            decoder.decode(hex);
            byte[] decoded = decoder.drain();
            
            int length = Math.min(len, decoded.length);
            
            System.arraycopy(decoded, 0, b, off, length);
            return length;
        }
        
        @Override
        public String readLine() throws IOException
        {
            return null;
        }

        @Override
        public void readFully(byte[] b) throws IOException
        {
        }
  
        @Override
        public void readFully(byte[] b, int off, int len) throws IOException
        {
        }
  
        @Override
        public int skipBytes(int n) throws IOException
        {
            return 0;
        }

        @Override
        public long skip(long n) throws IOException
        {
            return 0;
        }
  
        @Override
        public int available() throws IOException
        {
            return 0;
        }
  
        @Override
        public void close() throws IOException
        {
        }

        private String readElementText(String expectedName)
        {
            try
            {
                xmlStreamReader.nextTag();
                
                String name = xmlStreamReader.getLocalName();
  
                if (name.equals(expectedName))
                {
                    return xmlStreamReader.getElementText();
                }
                else
                {
                    throw new MessageException("Expected \"" + expectedName + "\" element but found \"" + name + "\" (at " + xmlStreamReader.getLocation() + ")");
                }
            }
            catch (XMLStreamException ex)
            {
                throw new MessageException("Failed to read input XML.", ex);
            }
        }
    }
}
