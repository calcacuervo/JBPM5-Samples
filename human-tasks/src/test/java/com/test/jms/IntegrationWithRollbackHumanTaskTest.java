package com.test.jms;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;

import javax.jms.ConnectionFactory;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.HeuristicMixedException;
import javax.transaction.HeuristicRollbackException;
import javax.transaction.NotSupportedException;
import javax.transaction.RollbackException;
import javax.transaction.SystemException;
import javax.transaction.TransactionManager;

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
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
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
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.builder.ProcessBuilderFactoryServiceImpl;
import org.jbpm.process.instance.ProcessRuntimeFactoryServiceImpl;
import org.jbpm.process.workitem.wsht.CommandBasedWSHumanTaskHandler;
import org.jbpm.task.Group;
import org.jbpm.task.Status;
import org.jbpm.task.User;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServer;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.jms.JMSTaskClientHandler;
import org.jnp.server.Main;
import org.jnp.server.NamingBeanImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import bitronix.tm.resource.jms.PoolingConnectionFactory;

import com.test.MockUserInfo;
import com.test.TaskClientWrapper;

public class IntegrationWithRollbackHumanTaskTest {
	public final static String PROCESSES_PKG_KEY = "processes";
	private static final Logger logger = LoggerFactory
			.getLogger(BaseJMSTaskServer.class);
	protected TaskServer server;
	protected TaskClientWrapper client;
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

	private JMSServerManager jmsServer;

	private Main jndiServer;

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
		startHornet();

		final PoolingConnectionFactory pcf = new PoolingConnectionFactory();
		pcf.setClassName("bitronix.tm.resource.jms.JndiXAConnectionFactory");
		pcf.setUniqueName("hornet");
		pcf.setMaxPoolSize(500);
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
				.lookup("hornet");
		this.connectionFactory = (PoolingConnectionFactory) factory;

		ds1 = new PoolingDataSource();
		ds1.setUniqueName("jdbc/testDS1");
		ds1.setClassName("org.h2.jdbcx.JdbcDataSource");
		ds1.setMaxPoolSize(3);
		ds1.setAllowLocalTransactions(true);
		ds1.getDriverProperties().put("user", "sa");
		ds1.getDriverProperties().put("password", "sasa");
		ds1.getDriverProperties().put("URL", "jdbc:h2:mem:mydb");
		ds1.init();

		emf = Persistence
				.createEntityManagerFactory("org.jbpm.persistence.jpa");

		env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());

		System.setProperty("java.naming.factory.initial",
				"org.jnp.interfaces.NamingContextFactory");
		TransactionManager btm = TransactionManagerServices
				.getTransactionManager();
		Properties serverProperties = new Properties();
		serverProperties.setProperty("JMSTaskServer.connectionFactory",
				"hornet");
		serverProperties.setProperty("JMSTaskServer.transacted", "true");
		serverProperties.setProperty("JMSTaskServer.acknowledgeMode",
				"AUTO_ACKNOWLEDGE");
		serverProperties.setProperty("JMSTaskServer.queueName", "tasksQueue");
		serverProperties.setProperty("JMSTaskServer.responseQueueName",
				"tasksResponseQueue");

		emfTask = Persistence.createEntityManagerFactory("org.jbpm.task");
		taskService = new TaskService(emfTask,
				SystemEventListenerFactory.getSystemEventListener());
		taskSession = taskService.createSession();
		System.setProperty("java.naming.factory.initial",
				"bitronix.tm.jndi.BitronixInitialContextFactory");
		Context ctx = new InitialContext();
		this.server = new JMSTaskServer(taskService, serverProperties, ctx, btm);
		Thread thread = new Thread(server);
		thread.start();

		MockUserInfo userInfo = new MockUserInfo();

		taskService.setUserinfo(userInfo);

		this.fillUsersAndGroups(taskSession);

		Properties clientProperties = new Properties();
		clientProperties.setProperty("JMSTaskClient.connectionFactory",
				"hornet");
		clientProperties.setProperty("JMSTaskClient.transactedQueue", "true");
		clientProperties.setProperty("JMSTaskClient.acknowledgeMode",
				"AUTO_ACKNOWLEDGE");
		clientProperties.setProperty("JMSTaskClient.queueName", "tasksQueue");
		clientProperties.setProperty("JMSTaskClient.responseQueueName",
				"tasksResponseQueue");
		TaskClient taskClient = new TaskClient(new JMSTaskClientConnector(
				"testConnector", new JMSTaskClientHandler(
						SystemEventListenerFactory.getSystemEventListener()),
				clientProperties, ctx, btm, false));
		this.client = new TaskClientWrapper(taskClient, btm);
		try {
			this.client.connect("127.0.0.1", 5445);
		} catch (IllegalStateException e) {
			// Already connected
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
			jndiServer = new Main();
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
					"tasksQueue", null, false, "/queue/TasksQueue");
			jmsConfig.getQueueConfigurations().add(queueConfig);
			queueConfig = new QueueConfigurationImpl("tasksResponseQueue",
					null, false, "/queue/TasksResponseQueue");
			jmsConfig.getQueueConfigurations().add(queueConfig);

			// Step 8. Start the JMS Server using the HornetQ core server and
			// the JMS configuration
			jmsServer = new JMSServerManagerImpl(
					hornetqServer, jmsConfig);
			jmsServer.start();
			System.out.println("Started Embedded JMS Server");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	@After
	public void tearDown() throws Exception {
		System.out.println("TEARING DOWN");
		if (this.fileLogger != null) {
			this.fileLogger.close();
		}

		// emf.close();
		// if (emfTask != null) {
		// emfTask.close();
		// }
		connectionFactory.close();
		context.close();
		// ds1.close();

		server.stop();
		this.client.disconnect();
		this.jndiServer.stop();
		System.setProperty("java.naming.factory.initial",
		"org.jnp.interfaces.NamingContextFactory");
		this.jmsServer.setContext(new InitialContext());
		this.jmsServer.stop();
	}

	protected String[] getTestUsers() {
		return new String[] { "usr0", "testUser1", "testUser2", "testUser3",
				"Administrator" };
	}

	protected String[] getTestGroups() {
		return new String[] { "testGroup1", "testGroup2" };
	}

	protected String[] getProcessPaths() {
		return new String[] { "failing-test.bpmn" };
	}

	private void fillUsersAndGroups(TaskServiceSession session) {
		for (String group : this.getTestGroups()) {
			session.addGroup(new Group(group));
		}
		for (String user : this.getTestUsers()) {
			session.addUser(new User(user));
		}
	}

	@Test
	public void jbpmHornetQWithTx() throws InterruptedException,
			NotSupportedException, SystemException, SecurityException, IllegalStateException, RollbackException, HeuristicMixedException, HeuristicRollbackException {
		TransactionManager btm = TransactionManagerServices
				.getTransactionManager();
		KnowledgeBase kbase = this.createKnowledgeBase();
		StatefulKnowledgeSession session = JPAKnowledgeService
				.newStatefulKnowledgeSession(kbase, null, env);

		// this will log in audit tables
		new JPAWorkingMemoryDbLogger(session);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);

		// Logger that will give information about the process state, variables,
		// etc
		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
				session.getEnvironment());

		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		session.getWorkItemManager().registerWorkItemHandler("Fail",
				new FailingHandler());
		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);
		List<String> groupsUser1 = new ArrayList<String>();
		groupsUser1.add("testGroup1");
		List<String> groupsUser2 = new ArrayList<String>();
		groupsUser2.add("testGroup2");
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1", "en-UK", groupsUser1);

		Assert.assertEquals(1, tasks.size());
		this.fullCycleCompleteTask(tasks.get(0).getId(), "testUser1",
				groupsUser1);

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());

		for (NodeInstanceLog log : processLog.findNodeInstances(processInstanceId)) {
			System.out.println(log);
		}
//		
//		btm.begin();
//		btm.commit();
//		tasks = client.getTasksAssignedAsPotentialOwner("testUser2", "en-UK",
//				groupsUser2);
//
//		Assert.assertEquals(1, tasks.size());
//
//		 Thread.sleep(1000);
//		 this.fullCycleCompleteTask(tasks.get(0).getId(), "testUser2",
//		 groupsUser2);
//		 Thread.sleep(3000);
//		// //now check in the logs the process finished.
//		 ProcessInstanceLog processInstanceLog =
//		 processLog.findProcessInstance(processInstanceId);
//		 Assert.assertNotNull(processInstanceLog.getEnd());
	}

	private void fullCycleCompleteTask(long taskId, String userId,
			List<String> groups) {
		client.claim(taskId, userId, groups);
		client.start(taskId, userId);
		client.complete(taskId, userId, null);

	}

}
