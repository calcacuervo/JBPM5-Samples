package com.test.jms;

import java.io.IOException;
import java.io.Serializable;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.MessageProducer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;

import org.jbpm.task.service.SessionWriter;
import org.jbpm.task.service.jms.TaskServiceConstants;

public class JMSSessionWriter implements SessionWriter {
	private final String selector;
	private final Connection connection;

	public JMSSessionWriter(Connection connection, String selector) {
		this.connection = connection;
		this.selector = selector;
	}

	public void write(Object message) throws IOException {
		Session session = null;
		MessageProducer producer = null;
		Queue queue;
		try {
			session = connection.createSession(true, Session.AUTO_ACKNOWLEDGE);
			queue = session.createQueue("tasksResponseQueue");

			producer = session.createProducer(queue);

			ObjectMessage clientMessage = session.createObjectMessage();
			clientMessage.setObject((Serializable) message);

			clientMessage.setStringProperty(TaskServiceConstants.SELECTOR_NAME,
					this.selector);
			producer.send(clientMessage);
		} catch (JMSException e) {
			throw new IOException(
					"Unable to create message: " + e.getMessage(), e);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (session != null) {
				try {
					session.close();
				} catch (JMSException e) {
					throw new RuntimeException(
							"There was an error when closing session", e);
				}
			}
			if (producer != null) {
				try {
					producer.close();
				} catch (JMSException e) {
					throw new RuntimeException(
							"There was an error when closing producer", e);
				}
			}
			
		}
	}
}
