package com.test.jms;

import java.util.HashMap;
import java.util.Map;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.Topic;

import org.drools.SystemEventListener;
import org.jbpm.task.service.TaskServerHandler;
import org.jbpm.task.service.TaskService;

public class JMSTaskServerHandler {
	
	private TaskServerHandler handler;
	private Map<String, MessageProducer> producers;

	public JMSTaskServerHandler(TaskService service, SystemEventListener systemEventListener) {
		this.handler = new TaskServerHandler(service, systemEventListener);
		this.producers = new HashMap<String, MessageProducer>();
	}

	public void messageReceived(Connection connection, Session session, Object message, Destination destination, String selector) throws Exception {
		String name = "";
		if (destination instanceof Queue) {
			name = ((Queue) destination).getQueueName();
		} else if (destination instanceof Topic) {
			name = ((Topic) destination).getTopicName();
		}
		MessageProducer producer = (MessageProducer) this.producers.get(name);
		if (producer == null) {
			producer = session.createProducer(destination);
			this.producers.put(name, producer);
		}
		System.out.println("{{{{{MessageReceived in server!");
		this.handler.messageReceived(new JMSSessionWriter(connection, selector), message);
	}
}
