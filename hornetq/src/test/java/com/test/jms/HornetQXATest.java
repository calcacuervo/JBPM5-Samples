package com.test.jms;

import java.io.IOException;
import java.util.Hashtable;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.naming.Context;
import javax.naming.InitialContext;

import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.core.config.Configuration;
import org.hornetq.core.config.impl.ConfigurationImpl;
import org.hornetq.core.server.HornetQServer;
import org.hornetq.core.server.HornetQServers;
import org.hornetq.integration.transports.netty.NettyAcceptorFactory;
import org.hornetq.integration.transports.netty.NettyConnectorFactory;
import org.hornetq.jms.server.JMSServerManager;
import org.hornetq.jms.server.config.ConnectionFactoryConfiguration;
import org.hornetq.jms.server.config.JMSConfiguration;
import org.hornetq.jms.server.config.QueueConfiguration;
import org.hornetq.jms.server.config.impl.ConnectionFactoryConfigurationImpl;
import org.hornetq.jms.server.config.impl.JMSConfigurationImpl;
import org.hornetq.jms.server.config.impl.QueueConfigurationImpl;
import org.hornetq.jms.server.impl.JMSServerManagerImpl;
import org.jnp.server.Main;
import org.jnp.server.NamingBeanImpl;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jms.PoolingConnectionFactory;

public class HornetQXATest {

	private PoolingConnectionFactory connectionFactory;
	private static final Logger logger = LoggerFactory
			.getLogger(HornetQXATest.class);
	private Context context;
	private Queue queue;
	private Connection connection;
	private Session session;
	private MessageConsumer consumer;
	private MessageProducer producer;

	@Before
	public void setUp() {
		startHornet();
		connectionFactory = new PoolingConnectionFactory();
		connectionFactory.setUniqueName("hornet");
		connectionFactory
				.setClassName("bitronix.tm.resource.jms.JndiXAConnectionFactory");
		connectionFactory.setMaxPoolSize(5);
		connectionFactory.setAllowLocalTransactions(true);
		connectionFactory.getDriverProperties().put("initialContextFactory",
				"org.jnp.interfaces.NamingContextFactory");
		connectionFactory.getDriverProperties().put("providerUrl",
				"jnp://localhost:1099");
		connectionFactory.getDriverProperties().put(
				"extraJndiProperties.java.naming.factory.url.pkgs",
				"org.jboss.naming:org.jnp.interfaces");
		connectionFactory.getDriverProperties().put("name",
				"XAConnectionFactory");
		connectionFactory.setUseTmJoin(true);
		connectionFactory.init();
	}

	@Test
	@Ignore("It hangs!")
	public void hornetQWithBitronix() {
		try {
			Context ctx = this.context;
			if (this.context == null) {
				ctx = new InitialContext();
			}

			String connFactoryName = "XAConnectionFactory";
			boolean transacted = true;
			int ackMode = Session.AUTO_ACKNOWLEDGE;
			String queueName = "testQueue";

			ConnectionFactory factory = (ConnectionFactory) ctx
					.lookup(connFactoryName);

			connection = factory.createConnection();
			this.connection.start();
			session = connection.createSession(transacted, ackMode);
			this.queue = session.createQueue(queueName);
			this.consumer = this.session.createConsumer(this.queue);
			this.producer = this.session.createProducer(this.queue);
			TextMessage message = this.session.createTextMessage("Test");
			if (TransactionManagerServices.getTransactionManager()
					.getCurrentTransaction() == null) {
				TransactionManagerServices.getTransactionManager().begin();
			}

			producer.send(message);
			TransactionManagerServices.getTransactionManager().commit();
			TextMessage clientMessage = (TextMessage) consumer.receive();
			if (clientMessage != null) {
				String msg = readMessage(clientMessage);
				System.out.println("RECEIVED MESSAGE " + msg);
			}

		} catch (JMSException e) {
			if ("102".equals(e.getErrorCode())) {
				logger.info(e.getMessage());
			} else {
				logger.error("JMS exception. ", e);
			}
		} catch (Exception e) {
			throw new RuntimeException("Error leyendo mensaje", e);
		}
	}

	private String readMessage(Message msgReceived) throws IOException {
		TextMessage strmMsgReceived = (TextMessage) msgReceived;
		try {
			return strmMsgReceived.getText();
		} catch (JMSException e) {
			throw new IOException("Error reading message");
		}
	}

	private void startHornet() {
		try {

			// Step 1. Create HornetQ core configuration, and set the properties
			// accordingly
			Configuration configuration = new ConfigurationImpl();
			configuration.setPersistenceEnabled(false);
			configuration.setSecurityEnabled(false);
			configuration.getAcceptorConfigurations().add(
					new TransportConfiguration(NettyAcceptorFactory.class
							.getName()));

			// Step 2. Create HornetQ core server
			HornetQServer hornetqServer = HornetQServers
					.newHornetQServer(configuration);
			// Step 3. Create and start the JNDI server
			System.setProperty("java.naming.factory.initial",
					"org.jnp.interfaces.NamingContextFactory");
			NamingBeanImpl naming = new NamingBeanImpl();
			naming.start();
			System.setProperty("java.naming.factory.initial",
					"bitronix.tm.jndi.BitronixInitialContextFactory");
			Main jndiServer = new Main();
			jndiServer.setNamingInfo(naming);
			jndiServer.setPort(1099);
			jndiServer.setBindAddress("localhost");
			jndiServer.setRmiPort(1098);
			jndiServer.setRmiBindAddress("localhost");
			jndiServer.start();

			// Step 4. Create the JMS configuration
			JMSConfiguration jmsConfig = new JMSConfigurationImpl();

			// Step 5. Configure context used to bind the JMS resources to JNDI
			Hashtable<String, String> env = new Hashtable<String, String>();
			env.put("java.naming.factory.initial",
					"org.jnp.interfaces.NamingContextFactory");
			env.put("java.naming.provider.url", "jnp://localhost:1099");
			env.put("java.naming.factory.url.pkgs",
					"org.jboss.naming:org.jnp.interfaces");
			context = new InitialContext(env);
			jmsConfig.setContext(context);

			// Step 6. Configure the JMS ConnectionFactory
			TransportConfiguration connectorConfig = new TransportConfiguration(
					NettyConnectorFactory.class.getName());
			ConnectionFactoryConfiguration cfConfig = new ConnectionFactoryConfigurationImpl(
					"XAConnectionFactory", connectorConfig,
					"XAConnectionFactory");
			jmsConfig.getConnectionFactoryConfigurations().add(cfConfig);

			// Step 7. Configure the JMS Queue
			QueueConfiguration queueConfig = new QueueConfigurationImpl(
					"testQueue", null, false, "/queue/TestQueue");
			jmsConfig.getQueueConfigurations().add(queueConfig);

			// Step 8. Start the JMS Server using the HornetQ core server and
			// the JMS configuration
			JMSServerManager jmsServer = new JMSServerManagerImpl(
					hornetqServer, jmsConfig);
			jmsServer.start();
			System.out.println("Started Embedded JMS Server");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

}
