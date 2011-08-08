package com.test.jms;

import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.SystemEventListenerFactory;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderError;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.compiler.ProcessBuilderFactory;
import org.drools.io.impl.ClassPathResource;
import org.drools.logger.KnowledgeRuntimeLogger;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.process.ProcessRuntimeFactory;
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
import org.jbpm.process.builder.ProcessBuilderFactoryServiceImpl;
import org.jbpm.process.instance.ProcessRuntimeFactoryServiceImpl;
import org.jbpm.task.Group;
import org.jbpm.task.User;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServer;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.jms.JMSTaskClientHandler;
import org.jnp.server.Main;
import org.jnp.server.NamingBeanImpl;
import org.junit.After;
import org.junit.Before;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import bitronix.tm.resource.jms.PoolingConnectionFactory;

import com.test.MockUserInfo;

public abstract class BaseHumanTaskTest {
	public final static String PROCESSES_PKG_KEY = "processes";
	protected TaskServer server;
	protected TaskClient client;
	protected TaskService taskService;
	protected TaskServiceSession taskSession;

	protected Context context;
	
	private KnowledgeRuntimeLogger fileLogger;

	static {
		ProcessBuilderFactory
				.setProcessBuilderFactoryService(new ProcessBuilderFactoryServiceImpl());
		ProcessRuntimeFactory
				.setProcessRuntimeFactoryService(new ProcessRuntimeFactoryServiceImpl());
	}
	private PoolingConnectionFactory connectionFactory;
	private PoolingDataSource ds1;
	private EntityManagerFactory emf;
	private EntityManagerFactory emfTask;
	protected Environment env;

	protected abstract String[] getProcessPaths();

	protected KnowledgeBase createKnowledgeBase() {
		KnowledgeBuilder kbuilder = KnowledgeBuilderFactory
				.newKnowledgeBuilder();

		for (String path : this.getProcessPaths()) {
			kbuilder.add(new ClassPathResource(path), ResourceType.BPMN2);
		}
		KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
		kbase.addKnowledgePackages(kbuilder.getKnowledgePackages());
		if (kbuilder.hasErrors()) {
			StringBuilder errorMessage = new StringBuilder();
			for (KnowledgeBuilderError error : kbuilder.getErrors()) {
				errorMessage.append(error.getMessage());
				errorMessage.append(System.getProperty("line.separator"));
			}
			Assert.fail(errorMessage.toString());
		}
		return kbase;
	}

	@Before
	public void setUp() throws Exception {
		startJornet();
		connectionFactory = new PoolingConnectionFactory();
		connectionFactory.setUniqueName("hornet");
		connectionFactory.setClassName("bitronix.tm.resource.jms.JndiXAConnectionFactory");
		connectionFactory.setMaxPoolSize(5);
		connectionFactory.setAllowLocalTransactions(true);
		connectionFactory.getDriverProperties().put("initialContextFactory", "org.jnp.interfaces.NamingContextFactory");
		connectionFactory.getDriverProperties().put("providerUrl", "jnp://localhost:1099");
		connectionFactory.getDriverProperties().put("extraJndiProperties.java.naming.factory.url.pkgs", "org.jboss.naming:org.jnp.interfaces");
		connectionFactory.getDriverProperties().put("name", "XAConnectionFactory");
		connectionFactory.setUseTmJoin(true);
		connectionFactory.init();
		
		ds1 = new PoolingDataSource();
		ds1.setUniqueName("jdbc/testDS1");
		ds1.setClassName("org.h2.jdbcx.JdbcDataSource");
		ds1.setMaxPoolSize(3);
		ds1.setAllowLocalTransactions(true);
		ds1.getDriverProperties().put("user", "sa");
		ds1.getDriverProperties().put("password", "sasa");
		ds1.getDriverProperties().put("URL", "jdbc:h2:mem:mydb");
		ds1.setUseTmJoin(true);
		ds1.init();
		
//	    System.setProperty("java.naming.factory.initial", "bitronix.tm.jndi.BitronixInitialContextFactory");
		emf = Persistence
				.createEntityManagerFactory("org.jbpm.persistence.jpa");

		env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());

		emfTask = Persistence.createEntityManagerFactory("org.jbpm.task");
		taskService = new TaskService(emfTask,
				SystemEventListenerFactory.getSystemEventListener());
		taskSession = taskService.createSession();
		MockUserInfo userInfo = new MockUserInfo();

		taskService.setUserinfo(userInfo);

		this.fillUsersAndGroups(taskSession);

		Properties serverProperties = new Properties();
		serverProperties.setProperty("JMSTaskServer.connectionFactory", "hornet");
		serverProperties.setProperty("JMSTaskServer.transacted", "true");
		serverProperties.setProperty("JMSTaskServer.acknowledgeMode", "AUTO_ACKNOWLEDGE");
		serverProperties.setProperty("JMSTaskServer.queueName", "tasksQueue");
		serverProperties.setProperty("JMSTaskServer.responseQueueName", "tasksResponseQueue");
//		System.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
		this.server = new JMSTaskServer(taskService, serverProperties, context);
		Thread thread = new Thread(server);
		thread.start();
		System.out.println("Waiting for the HornetQTask Server to come up");
		while (!server.isRunning()) {
			System.out.print(".");
			Thread.sleep(50);
		}

		Properties clientProperties = new Properties();
		clientProperties.setProperty("JMSTaskClient.connectionFactory", "hornet");
		clientProperties.setProperty("JMSTaskClient.transactedQueue", "true");
		clientProperties.setProperty("JMSTaskClient.acknowledgeMode", "AUTO_ACKNOWLEDGE");
		clientProperties.setProperty("JMSTaskClient.queueName", "tasksQueue");
		clientProperties.setProperty("JMSTaskClient.responseQueueName", "tasksResponseQueue");
		this.client = new TaskClient(new JMSTaskClientConnector("testConnector", new JMSTaskClientHandler(SystemEventListenerFactory
							.getSystemEventListener()), clientProperties, context));
		try {
			this.client.connect("127.0.0.1", 5445);
		} catch (IllegalStateException e) {
			// Already connected
		}
	}
	
	private void startJornet() {
		 try
	      {

	         // Step 1. Create HornetQ core configuration, and set the properties accordingly
	         Configuration configuration = new ConfigurationImpl();
	         configuration.setPersistenceEnabled(false);
	         configuration.setSecurityEnabled(false);
	         configuration.getAcceptorConfigurations()
	                      .add(new TransportConfiguration(NettyAcceptorFactory.class.getName()));

	         // Step 2. Create HornetQ core server
	         HornetQServer hornetqServer = HornetQServers.newHornetQServer(configuration);
	         // Step 3. Create and start the JNDI server
	         System.setProperty("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
	         NamingBeanImpl naming = new NamingBeanImpl();
	         naming.start();
	         System.setProperty("java.naming.factory.initial", "bitronix.tm.jndi.BitronixInitialContextFactory");
	         Main jndiServer = new  Main();
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
	         env.put("java.naming.factory.initial", "org.jnp.interfaces.NamingContextFactory");
	         env.put("java.naming.provider.url", "jnp://localhost:1099");
	         env.put("java.naming.factory.url.pkgs", "org.jboss.naming:org.jnp.interfaces");
	         context = new InitialContext(env);
	         jmsConfig.setContext(context);

	         // Step 6. Configure the JMS ConnectionFactory
	         TransportConfiguration connectorConfig = new TransportConfiguration(NettyConnectorFactory.class.getName());
	         ConnectionFactoryConfiguration cfConfig = new ConnectionFactoryConfigurationImpl("XAConnectionFactory", connectorConfig, "XAConnectionFactory");
	         jmsConfig.getConnectionFactoryConfigurations().add(cfConfig);

	         // Step 7. Configure the JMS Queue
	         QueueConfiguration queueConfig = new QueueConfigurationImpl("tasksQueue", null, false, "/queue/TasksQueue");
	         jmsConfig.getQueueConfigurations().add(queueConfig);
	         queueConfig = new QueueConfigurationImpl("tasksResponseQueue", null, false, "/queue/TasksResponseQueue");
	         jmsConfig.getQueueConfigurations().add(queueConfig);

	         // Step 8. Start the JMS Server using the HornetQ core server and the JMS configuration
	         JMSServerManager jmsServer = new JMSServerManagerImpl(hornetqServer, jmsConfig);
	         jmsServer.start();
	         System.out.println("Started Embedded JMS Server");

	      }
	      catch (Exception e)
	      {
	         e.printStackTrace();
	         System.exit(-1);
	      }	}

	@After
	public void tearDown() throws Exception {

		if (this.fileLogger != null) {
			this.fileLogger.close();
		}

		emf.close();
		// if (emfTask != null) {
		// emfTask.close();
		// }
		connectionFactory.close();
		ds1.close();

		server.stop();
		this.client.disconnect();
	}

	protected abstract String[] getTestUsers();

	protected abstract String[] getTestGroups();

	protected abstract Map<String, Set<String>> getTestUserGroupsAssignments();

	private void fillUsersAndGroups(TaskServiceSession session) {
		for (String group : this.getTestGroups()) {
			session.addGroup(new Group(group));
		}
		for (String user : this.getTestUsers()) {
			session.addUser(new User(user));
		}
	}
}
