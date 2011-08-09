package com.test.jms.another;

import java.util.Hashtable;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;

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
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.BitronixTransactionManager;
import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jms.PoolingConnectionFactory;

public class HornetQXATest {

	private PoolingConnectionFactory connectionFactory;
	private ConnectionFactory hornetQConnectionFactory;
	private static final Logger logger = LoggerFactory
			.getLogger(HornetQXATest.class);
	private Context context;

	@Test
	public void test() throws NamingException, NotSupportedException, SystemException, SecurityException, IllegalStateException, RollbackException, HeuristicMixedException, HeuristicRollbackException {
		startHornet();
		int threadCount = 3;

		final PoolingConnectionFactory pcf = new PoolingConnectionFactory();
		pcf.setClassName("bitronix.tm.resource.jms.JndiXAConnectionFactory");
		pcf.setUniqueName("hornet1");
		pcf.setMaxPoolSize(5);
		pcf.getDriverProperties().setProperty("name", "XAConnectionFactory");
		pcf.getDriverProperties().setProperty("initialContextFactory",
				"org.jnp.interfaces.NamingContextFactory");
		pcf.getDriverProperties().setProperty("providerUrl",
				"jnp://localhost:1099");
		pcf.getDriverProperties().setProperty(
				"extraJndiProperties.java.naming.factory.url.pkgs",
				"org.jboss.naming:org.jnp.interfaces");
		pcf.init();
		System.setProperty("java.naming.factory.initial",
				"bitronix.tm.jndi.BitronixInitialContextFactory");
		ConnectionFactory factory = (ConnectionFactory) new InitialContext()
				.lookup("hornet1");
		System.setProperty("java.naming.factory.initial",
				"org.jnp.interfaces.NamingContextFactory");
		this.connectionFactory = pcf;

		TransactionManagerServices.getConfiguration().setServerId(
				"hornet-consumer");
		TransactionManagerServices.getConfiguration()
				.setGracefulShutdownInterval(3);
		final BitronixTransactionManager btm = TransactionManagerServices
				.getTransactionManager();
		Consumer consumer = new Consumer(btm, factory, "testQueue");
		Thread thread = new Thread(consumer);
		thread.start();
		Producer producer = new Producer(btm, factory, "testQueue");
		btm.begin();
		producer.run();
		btm.commit();
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
