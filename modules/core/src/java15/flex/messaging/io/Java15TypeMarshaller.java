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
package flex.messaging.io;

import flex.messaging.io.amf.translator.ASTranslator;
import flex.messaging.io.amf.translator.decoder.EnumDecoder;

/**
 * A type marshaller that can handle Java 5 specific types (i.e. enum).
 * Provides the ability to convert between ASObjects used by
 * Flex and Java objects in your application.
 * @exclude
 */
public class Java15TypeMarshaller extends ASTranslator
{
    private static EnumDecoder enumDecoder = new EnumDecoder();

    /**
     * Creates the instance of the desired class.
     * Uses the source object if needed.
     * If the desired class is an enum, we create a shell type.
     * 
     * @param source the object we are starting with.
     * @param desiredClass the class we want.
     * @return a class instance.
     */
    public Object createInstance(Object source, Class desiredClass)
    {
        if (desiredClass.isEnum())
        {
            return enumDecoder.createShell(source, desiredClass);
        }
        else
        {
            return super.createInstance(source, desiredClass);
        }
    }

    /**
     * Translate an object to another object of the desired class.
     * Correctly handles enums.
     * 
     * @param source the object to convert.
     * @param desiredClass the target class.
     * @return the converted object.
     */
    public Object convert(Object source, Class desiredClass)
    {
        if (desiredClass.isEnum())
        {
            return enumDecoder.decodeObject(source, desiredClass);
        }
        else 
        {
            return super.convert(source, desiredClass);
        }
    }
    
}
