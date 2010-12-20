//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//

package com.rabbitmq.client.test.functional;

import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.GetResponse;
import com.rabbitmq.client.test.BrokerTestCase;

import java.io.IOException;
import java.util.Arrays;


//
// Functional test demonstrating use of an internal exchange in an exchange to
// exchange routing scenario.  The routing topology is:
//
//            -------            -------
//          -/       \-        -/       \-
//         /           \      /           \           +-------------+
//         |    e0     +------|     e1    +-----------+    q1       |
//         \           /      \           /           +-------------+
//          -\       /-        -\       /-
//            -------            -------
//                              (internal)
//
// Where a non-internal exchange is bound to an internal exchange, which in
// turn is bound to a queue.  A client should be able to publish to e0, but
// not to e1, and publications to e0 should be delivered into q1.
//
public class InternalExchange extends BrokerTestCase
{
    private final String[] queues = new String[] { "q1" };
    private final String[] exchanges = new String[] { "e0", "e1" };

    protected void createResources() throws IOException
    {
        // The queues and exchange we create here are all auto-delete, so we
        // don't need to override releaseResources() with their deletions...
        for (String q : queues)
        {
            channel.queueDeclare(q, false, true, true, null);
        }

        // The second exchange, "e1", will be an 'internal' one.
        for ( String e : exchanges )
        {
            channel.exchangeDeclare(e, "direct",
                                    false, true,
                                    !e.equals("e0"),
                                    null);
        }

        channel.exchangeBind("e1", "e0", "");
        channel.queueBind("q1", "e1", "");
    }


    public void testTryPublishingToInternalExchange()
            throws IOException
    {
        byte[] testDataBody = "test-data".getBytes();

        // We should be able to publish to the non-internal exchange as usual
        // and see our message land in the queue...
        channel.basicPublish("e0", "", null, testDataBody);
        GetResponse r = channel.basicGet("q1", true);
        assertTrue(Arrays.equals(r.getBody(), testDataBody));

        // Publishing to the internal exchange will not be allowed...
        channel.basicPublish("e1", "", null, testDataBody);
        try
        {
            // The channel should have shut down as a result of the forbidden
            // attempt to publish to an internal exchange...
            DefaultConsumer consumer = new DefaultConsumer(channel);
            channel.basicConsume("q1", consumer);
            fail("Channel should have shut down with 403 (access refused).");
        }
        catch (IOException e)
        {
            // We should get 403, access refused...
            checkShutdownSignal(403, e);
        }
    }
}