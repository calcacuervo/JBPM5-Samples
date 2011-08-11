package com.test;

import java.util.Map;
import java.util.Set;

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
import org.jbpm.process.builder.ProcessBuilderFactoryServiceImpl;
import org.jbpm.process.instance.ProcessRuntimeFactoryServiceImpl;
import org.jbpm.task.Group;
import org.jbpm.task.User;
import org.jbpm.task.service.TaskClient;
import org.jbpm.task.service.TaskServer;
import org.jbpm.task.service.TaskService;
import org.jbpm.task.service.TaskServiceSession;
import org.jbpm.task.service.hornetq.HornetQTaskClientConnector;
import org.jbpm.task.service.hornetq.HornetQTaskClientHandler;
import org.jbpm.task.service.hornetq.HornetQTaskServer;
import org.junit.After;
import org.junit.Before;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public abstract class BaseHumanTaskTest {
	public final static String PROCESSES_PKG_KEY = "processes";
	protected TaskServer server;
	protected TaskClientWrapper client;
	protected TaskService taskService;
	protected TaskServiceSession taskSession;

	private KnowledgeRuntimeLogger fileLogger;

	static {
		ProcessBuilderFactory
				.setProcessBuilderFactoryService(new ProcessBuilderFactoryServiceImpl());
		ProcessRuntimeFactory
				.setProcessRuntimeFactoryService(new ProcessRuntimeFactoryServiceImpl());
	}
	private PoolingDataSource ds1;
	private EntityManagerFactory emf;
	private EntityManagerFactory emfTask;
	protected Environment env;

	protected abstract String[] getProcessPaths();
	
	protected KnowledgeBase createKnowledgeBase() {
		KnowledgeBuilder kbuilder = KnowledgeBuilderFactory
				.newKnowledgeBuilder();

		for (String path : this.getProcessPaths()) {
			kbuilder.add(new ClassPathResource(path),
					ResourceType.BPMN2);
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
		// Compiles and persists all the .bpmn resources
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

		emfTask = Persistence.createEntityManagerFactory("org.jbpm.task");
		taskService = new TaskService(emfTask,
				SystemEventListenerFactory.getSystemEventListener());
		taskSession = taskService.createSession();
        MockUserInfo userInfo = new MockUserInfo();
        
        taskService.setUserinfo(userInfo);
        
		this.fillUsersAndGroups(taskSession);

		server = new HornetQTaskServer(taskService, 5446);
		Thread thread = new Thread(server);
		thread.start();
		System.out.println("Waiting for the HornetQTask Server to come up");
		while (!server.isRunning()) {
			System.out.print(".");
			Thread.sleep(50);
		}

		TaskClient taskClient = new TaskClient(new HornetQTaskClientConnector("client 1",
				new HornetQTaskClientHandler(SystemEventListenerFactory
						.getSystemEventListener())));
		this.client = new TaskClientWrapper(taskClient, null);
		this.client.connect("127.0.0.1", 5446);

	}

	@After
	public void tearDown() throws Exception {

		if (this.fileLogger != null) {
			this.fileLogger.close();
		}

		if (emf != null) {
			emf.close();
		}
		// if (emfTask != null) {
		// emfTask.close();
		// }
		if (ds1 != null) {
			ds1.close();
		}

		client.disconnect();
		server.stop();
	}

	protected abstract String[] getTestUsers();

	protected abstract String[] getTestGroups();

	protected abstract Map<String, Set<String>> getTestUserGroupsAssignments();
	
	private void fillUsersAndGroups(TaskServiceSession session) {
		for (int i = 0; i < this.getTestUsers().length; i++) {
			String testUser = this.getTestUsers()[i];
			session.addUser(new User(testUser));
		}
		
		for (int i = 0; i < this.getTestGroups().length; i++) {
			String testGroup = this.getTestGroups()[i];
			session.addGroup(new Group(testGroup));
		}
	}
}
