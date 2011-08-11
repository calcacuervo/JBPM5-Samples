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
import org.drools.runtime.Environment;
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
import org.jbpm.task.I18NText;
import org.jbpm.task.Status;
import org.jbpm.task.Task;
import org.jbpm.task.TaskData;
import org.jbpm.task.User;
import org.jbpm.task.service.ContentData;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServer;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.jms.JMSTaskClientHandler;
import org.jbpm.task.service.responsehandlers.BlockingAddTaskResponseHandler;
import org.jnp.server.Main;
import org.jnp.server.NamingBeanImpl;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;
import bitronix.tm.resource.jms.PoolingConnectionFactory;

import com.test.MockUserInfo;

public class BaseHumanTaskTest {
	public final static String PROCESSES_PKG_KEY = "processes";
	private static final Logger logger = LoggerFactory
			.getLogger(BaseJMSTaskServer.class);
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

	@Test
	public void humanTaskWithJMS() throws Exception {
		startJornet();

		final PoolingConnectionFactory pcf = new PoolingConnectionFactory();
		pcf.setClassName("bitronix.tm.resource.jms.JndiXAConnectionFactory");
		pcf.setUniqueName("hornet");
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
				.lookup("hornet");
		System.setProperty("java.naming.factory.initial",
				"org.jnp.interfaces.NamingContextFactory");
		this.connectionFactory = (PoolingConnectionFactory) factory;

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
		// System.setProperty("java.naming.factory.initial",
		// "org.jnp.interfaces.NamingContextFactory");
		emfTask = Persistence.createEntityManagerFactory("org.jbpm.task");
		taskService = new TaskService(emfTask,
				SystemEventListenerFactory.getSystemEventListener());
		taskSession = taskService.createSession();
		System.setProperty("java.naming.factory.initial",
				"bitronix.tm.jndi.BitronixInitialContextFactory");
		Context ctx = new InitialContext();
		this.server = new JMSTaskServer(taskService, serverProperties, ctx);
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
		ctx = new InitialContext();
		this.client = new TaskClient(new JMSTaskClientConnector(
				"testConnector", new JMSTaskClientHandler(
						SystemEventListenerFactory.getSystemEventListener()),
				clientProperties, ctx, btm, true));
		try {
			this.client.connect("127.0.0.1", 5445);
		} catch (IllegalStateException e) {
			// Already connected
		}
		TaskClient tc = this.client;
		Task task = new Task();
		List<I18NText> names1 = new ArrayList<I18NText>();
		I18NText text1 = new I18NText("en-UK", "tarea1");
		names1.add(text1);
		task.setNames(names1);
		TaskData taskData = new TaskData();
		taskData.setStatus(Status.Created);
		taskData.setCreatedBy(new User("usr0"));
		taskData.setActualOwner(new User("usr0"));
		task.setTaskData(taskData);
		ContentData data = new ContentData();
		Thread.sleep(2000);
		BlockingAddTaskResponseHandler addTaskHandler = new BlockingAddTaskResponseHandler();
		tc.addTask(task, data, addTaskHandler);
		System.out.println("-----GET TASK ID-----");
		Thread.sleep(2000);
		long taskId = addTaskHandler.getTaskId();
		Assert.assertEquals(1L, taskId);

	}

	private void startJornet() {
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
			jmsServer = new JMSServerManagerImpl(hornetqServer, jmsConfig);
			jmsServer.start();
			System.out.println("Started Embedded JMS Server");

		} catch (Exception e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}

	JMSServerManager jmsServer;

	Main jndiServer;

	@After
	public void tearDown() throws Exception {

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
		return new String[] { "usr0", "testUser2", "testUser3", "Administrator" };
	}

	protected String[] getTestGroups() {
		return new String[] { "testGroup1", "testGroup2" };
	}

	protected String[] getProcessPaths() {
		return new String[] {/**
		 * "two-tasks-human-task-test.bpmn",
		 * "two-tasks-human-task-assigned-to-actortest.bpmn"
		 **/
		};
	}

	private void fillUsersAndGroups(TaskServiceSession session) {
		for (String group : this.getTestGroups()) {
			session.addGroup(new Group(group));
		}
		for (String user : this.getTestUsers()) {
			session.addUser(new User(user));
		}
	}
}
