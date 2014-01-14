/*************************************************************************
 *
 * ADOBE CONFIDENTIAL
 * __________________
 *
 *  Copyright 2008 Adobe Systems Incorporated
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
package flex.messaging.io.amfx.string_tag;

import flex.messaging.io.amfx.DeserializationConfirmation;
import flex.messaging.io.amf.ActionMessage;
import flex.messaging.io.amf.MessageBody;
import flex.messaging.io.amf.ASObject;
import flex.messaging.MessageException;

public class Confirm11d extends DeserializationConfirmation
{
    private ActionMessage EXPECTED_VALUE;

    public Confirm11d()
    {
        ActionMessage m = new ActionMessage();
        MessageBody body = new MessageBody();
        m.addBody(body);

        ASObject aso = new ASObject();
        aso.put("prop0", "  Alpha");
        aso.put("prop1", "Beta  ");
        aso.put("prop2", "  Gamma  ");
        aso.put("prop3", "\nDelta");
        aso.put("prop4", "Epsilon\n");
        aso.put("prop5", "\nZeta\n");
        aso.put("prop6", "\tEta");
        aso.put("prop7", "Theta\t");
        aso.put("prop8", "\tIota\t");
        aso.put("prop9", "\n\t      Kappa\n\n            ");

        body.setData(aso);
        EXPECTED_VALUE = m;
    }

    public ActionMessage getExpectedMessage()
    {
        return EXPECTED_VALUE;
    }

    public MessageException getExpectedException()
    {
        return null;
    }
}
