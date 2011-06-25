package com.test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.InitialContext;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.transaction.UserTransaction;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderError;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.compiler.ProcessBuilderFactory;
import org.drools.io.impl.ClassPathResource;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
import org.drools.runtime.process.ProcessRuntimeFactory;
import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.audit.NodeInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.process.builder.ProcessBuilderFactoryServiceImpl;
import org.jbpm.process.instance.ProcessRuntimeFactoryServiceImpl;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class ProcessExecutionTest {

	static {
		ProcessBuilderFactory
				.setProcessBuilderFactoryService(new ProcessBuilderFactoryServiceImpl());
		ProcessRuntimeFactory
				.setProcessRuntimeFactoryService(new ProcessRuntimeFactoryServiceImpl());
	}
	private PoolingDataSource ds1;
	private EntityManagerFactory emf;

	@Before
	public void setUp() throws Exception {
		ds1 = new PoolingDataSource();
		ds1.setUniqueName("jdbc/testDS1");
		ds1.setClassName("org.h2.jdbcx.JdbcDataSource");
		ds1.setMaxPoolSize(3);
		ds1.setAllowLocalTransactions(true);
		ds1.getDriverProperties().put("user", "sa");
		ds1.getDriverProperties().put("password", "sasa");
		ds1.getDriverProperties().put("URL", "jdbc:h2:mem:mydb");
		ds1.init();

		 ds1.init();

		emf = Persistence
				.createEntityManagerFactory("org.jbpm.persistence.jpa");

	}

	@After
	public void tearDown() throws Exception {
		emf.close();
		ds1.close();
	}

	private KnowledgeBase createKnowledgeBase() {
		KnowledgeBuilder kbuilder = KnowledgeBuilderFactory
				.newKnowledgeBuilder();
		kbuilder.add(new ClassPathResource("com/test_flow.bpmn"),
				ResourceType.BPMN2);
		kbuilder.add(
				new ClassPathResource("com/exceeds.drl"),
				ResourceType.DRL);
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
	public void testWithFireUntilHalt() throws Exception {
		KnowledgeBase kbase = createKnowledgeBase();
		EntityManagerFactory emf = Persistence
				.createEntityManagerFactory("org.jbpm.persistence.jpa");
		Environment env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());
		final StatefulKnowledgeSession session = JPAKnowledgeService
				.newStatefulKnowledgeSession(kbase, null, env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		JPAProcessInstanceDbLog log = new JPAProcessInstanceDbLog(env);
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				new WorkItemHandler() {

					public void executeWorkItem(WorkItem workItem,
							WorkItemManager manager) {
						Map<String, Object> results = new HashMap<String, Object>();
						results.put("Result", "ResultValue");
						manager.completeWorkItem(workItem.getId(), results);
					}

					public void abortWorkItem(WorkItem workItem,
							WorkItemManager manager) {
					}
				});
		final UserTransaction ut = (UserTransaction) new InitialContext().lookup( "java:comp/UserTransaction" );
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("limit", new Long(1));
		parameters.put("accuralCount", new Long(2));


		new Thread(new Runnable() {
			
			@Override
			public void run() {
				session.fireUntilHalt();
			}
		}).start();

		ProcessInstance process = session.createProcessInstance(
				"test", parameters);
		session.insert(process);

		long processInstanceId = process.getId();
		Thread.sleep(5000);
		session.startProcessInstance(processInstanceId);

		List<NodeInstanceLog> nodes= log.findNodeInstances(processInstanceId);
		boolean endfound = false;
		for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
			NodeInstanceLog variableInstanceLog = (NodeInstanceLog) iterator
					.next();
			if (variableInstanceLog.getNodeName().equalsIgnoreCase("end")) {
				endfound = true;
			}
			System.out.println(variableInstanceLog);
		}
		Assert.assertTrue(endfound);
		List<VariableInstanceLog> variables = log.findVariableInstances(processInstanceId);
		boolean found = false;
		for (Iterator iterator = variables.iterator(); iterator.hasNext();) {
			VariableInstanceLog variableInstanceLog = (VariableInstanceLog) iterator
					.next();
			if (variableInstanceLog.getVariableInstanceId().equalsIgnoreCase("exceeds")) {
				Assert.assertTrue(variableInstanceLog.getValue().equalsIgnoreCase("true"));
				found = true;
			}
			System.out.println(variableInstanceLog);
		}
		Assert.assertTrue(found);
	}
	
	@Test
	public void testWithFireAllRules() throws Exception {
		KnowledgeBase kbase = createKnowledgeBase();
		EntityManagerFactory emf = Persistence
				.createEntityManagerFactory("org.jbpm.persistence.jpa");
		Environment env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());
		final StatefulKnowledgeSession session = JPAKnowledgeService
				.newStatefulKnowledgeSession(kbase, null, env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		JPAProcessInstanceDbLog log = new JPAProcessInstanceDbLog(env);
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				new WorkItemHandler() {

					public void executeWorkItem(WorkItem workItem,
							WorkItemManager manager) {
						Map<String, Object> results = new HashMap<String, Object>();
						results.put("Result", "ResultValue");
						manager.completeWorkItem(workItem.getId(), results);
					}

					public void abortWorkItem(WorkItem workItem,
							WorkItemManager manager) {
					}
				});
		
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("limit", new Long(1));
		parameters.put("accuralCount", new Long(2));

		ProcessInstance process = session.createProcessInstance(
				"test", parameters);
		session.insert(process);
		long processInstanceId = process.getId();
//		new Thread(new Runnable() {
//			
//			@Override
//			public void run() {
//				session.fireUntilHalt();
//			}
//		}).start();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(5000);
		session.fireAllRules();

		List<NodeInstanceLog> nodes= log.findNodeInstances(processInstanceId);
		boolean endfound = false;
		for (Iterator iterator = nodes.iterator(); iterator.hasNext();) {
			NodeInstanceLog variableInstanceLog = (NodeInstanceLog) iterator
					.next();
			if (variableInstanceLog.getNodeName().equalsIgnoreCase("end")) {
				endfound = true;
			}
			System.out.println(variableInstanceLog);
		}
		Assert.assertTrue(endfound);
		
		List<VariableInstanceLog> variables = log.findVariableInstances(processInstanceId);
		boolean found = false;
		for (Iterator iterator = variables.iterator(); iterator.hasNext();) {
			VariableInstanceLog variableInstanceLog = (VariableInstanceLog) iterator
					.next();
			if (variableInstanceLog.getVariableInstanceId().equalsIgnoreCase("exceeds")) {
				Assert.assertTrue(variableInstanceLog.getValue().equalsIgnoreCase("true"));
				found = true;
			}
			System.out.println(variableInstanceLog);
		}
		Assert.assertTrue(found);
	}
	
	
	

}