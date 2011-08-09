package com.test.jms.extra;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

public class JMSHelper {
    public static Message readMessage(ConnectionFactory connectionFactory, String queueName, long timeout) throws JMSException {
        Connection connection = connectionFactory.createConnection();
        connection.start();
        Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue(queueName);
        MessageConsumer consumer = session.createConsumer(destination);
        try {
            return consumer.receive(200000);
        } finally {
            consumer.close();
            session.close();
            connection.close();
        }
    }
    
    public static void sendSampleMessage(ConnectionFactory connectionFactory, String queueName, long timeout) throws JMSException {
        Connection connection = connectionFactory.createConnection();
        connection.start();
        Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
        Destination destination = session.createQueue(queueName);
        MessageProducer producer = session.createProducer(destination);
        try {
    		TextMessage message = session.createTextMessage("Test");
            producer.send(message);
        } finally {
            producer.close();
            session.close();
            connection.close();
        }
    }

}
