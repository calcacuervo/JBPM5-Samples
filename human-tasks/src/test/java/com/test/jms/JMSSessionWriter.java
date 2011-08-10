package com.test.jms;

import java.io.IOException;
import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.InitialContext;

import org.jbpm.task.service.SessionWriter;
import org.jbpm.task.service.jms.TaskServiceConstants;

import bitronix.tm.TransactionManagerServices;

public class JMSSessionWriter implements SessionWriter {
	private final Session session;
	private final MessageProducer producer;
	private final String selector;

	public JMSSessionWriter(Session session, MessageProducer producer, String selector) {
		this.session = session;
		this.producer = producer;
		this.selector = selector;
	}

	public void write(Object message) throws IOException {
		try {
//			TransactionManagerServices.getTransactionManager().begin();
			ConnectionFactory factory = (ConnectionFactory) new InitialContext().lookup("hornet");

			Connection connection = factory.createConnection();
			connection.start();
			Session session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			Queue queue = session.createQueue("tasksResponseQueue");
			
			MessageProducer producer = session.createProducer(queue);

			ObjectMessage clientMessage = session.createObjectMessage();
			clientMessage.setObject((Serializable) message);
			
			clientMessage.setStringProperty(TaskServiceConstants.SELECTOR_NAME, this.selector);
			producer.send(clientMessage);
//			TransactionManagerServices.getTransactionManager().commit();
		} catch (JMSException e) {
			throw new IOException("Unable to create message: " + e.getMessage(), e);
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
}
