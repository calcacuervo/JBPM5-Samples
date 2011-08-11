package com.test.jms;

import java.io.IOException;
import java.util.Properties;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.ObjectMessage;
import javax.jms.Queue;
import javax.jms.Session;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.transaction.TransactionManager;

import org.jbpm.task.service.TaskServer;
import org.jbpm.task.service.jms.TaskServiceConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.TransactionManagerServices;

import com.test.jms.extra.Consumer;

public abstract class BaseJMSTaskServer extends TaskServer {

	private boolean running;
	private static final Logger logger = LoggerFactory
			.getLogger(BaseJMSTaskServer.class);
	private JMSTaskServerHandler handler;
	private Properties connectionProperties;
	private Context context;
	private Connection connection;
	private Session session;
	private MessageConsumer consumer;

	public BaseJMSTaskServer(JMSTaskServerHandler handler,
			Properties properties, Context context) {
		this.handler = handler;
		this.connectionProperties = properties;
		this.context = context;
	}

	private Object readMessage(Message msgReceived) throws IOException {
		ObjectMessage strmMsgReceived = (ObjectMessage) msgReceived;
		try {
			return strmMsgReceived.getObject();
		} catch (JMSException e) {
			throw new IOException("Error reading message");
		}
	}

	private String readSelector(Message msgReceived) throws JMSException {
		return msgReceived
				.getStringProperty(TaskServiceConstants.SELECTOR_NAME);
	}

	public void start() throws Exception {
	}

	public void stop() throws Exception {
		if (this.running) {
			this.running = false;
			closeAll();
		}
	}

	public void run() {
		try {
			String connFactoryName = this.connectionProperties
					.getProperty(TaskServiceConstants.TASK_SERVER_CONNECTION_FACTORY_NAME);
			boolean transacted = Boolean
					.valueOf(this.connectionProperties
							.getProperty(TaskServiceConstants.TASK_SERVER_TRANSACTED_NAME));
			String ackModeString = this.connectionProperties
					.getProperty(TaskServiceConstants.TASK_SERVER_ACKNOWLEDGE_MODE_NAME);
			String queueName = this.connectionProperties
					.getProperty(TaskServiceConstants.TASK_SERVER_QUEUE_NAME_NAME);
			String responseQueueName = this.connectionProperties
					.getProperty(TaskServiceConstants.TASK_SERVER_RESPONSE_QUEUE_NAME_NAME);
			int ackMode = Session.DUPS_OK_ACKNOWLEDGE; // default
			if ("AUTO_ACKNOWLEDGE".equals(ackModeString)) {
				ackMode = Session.AUTO_ACKNOWLEDGE;
			} else if ("CLIENT_ACKNOWLEDGE".equals(ackModeString)) {
				ackMode = Session.CLIENT_ACKNOWLEDGE;
			}

			Context ctx = this.context;
			if (this.context == null) {
				ctx = new InitialContext();
			}
			ConnectionFactory factory = (ConnectionFactory) ctx
					.lookup(connFactoryName);
			this.running = true;
			this.connection = factory.createConnection();
			connection.start();
			while (this.isRunning()) {
				if (TransactionManagerServices.getTransactionManager()
						.getCurrentTransaction() == null) {
					TransactionManagerServices.getTransactionManager().begin();
				}
				this.session = connection.createSession(transacted,
						ackMode);
				Destination destination = session.createQueue(queueName);
				Destination responseQueue = session
						.createQueue(responseQueueName);
				System.out.println("Session:" + this.session);
				this.consumer = session.createConsumer(destination);
				Message clientMessage = null;
				try {
					clientMessage = consumer.receive();
					if (clientMessage != null) {
						System.out.println("received message ");
						Object object = readMessage(clientMessage);
						String selector = readSelector(clientMessage);
						this.handler.messageReceived(connection, session, object,
								responseQueue, selector);
						TransactionManagerServices.getTransactionManager()
						.commit();
					}
				} finally {
					consumer.close();
					session.close();
					connection.close();
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	private void closeAll() throws JMSException {
		this.consumer.close();
		this.connection.close();
	}

	public boolean isRunning() {
		return this.running;
	}
}
