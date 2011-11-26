package com.test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
import org.drools.base.MapGlobalResolver;
import org.drools.builder.KnowledgeBuilder;
import org.drools.builder.KnowledgeBuilderError;
import org.drools.builder.KnowledgeBuilderFactory;
import org.drools.builder.ResourceType;
import org.drools.io.impl.ClassPathResource;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.Environment;
import org.drools.runtime.EnvironmentName;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.audit.VariableInstanceLog;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class ProcessExecutionTest {

	private PoolingDataSource ds1;
	private EntityManagerFactory emf;
	private StatefulKnowledgeSession session;

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
		kbuilder.add(new ClassPathResource("com/exceeds.drl"), ResourceType.DRL);
		kbuilder.add(new ClassPathResource("com/test_flow_1.bpmn"),
				ResourceType.BPMN2);
		kbuilder.add(new ClassPathResource("com/stateless_test.bpmn"),
				ResourceType.BPMN2);
		kbuilder.add(new ClassPathResource("com/stateless_exceeds_rule.drl"),
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
	@Ignore("Fix it")
	public void test_rule_with_stateful() throws Exception {
		KnowledgeBase kbase = createKnowledgeBase();

		Environment env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());
		env.set(EnvironmentName.GLOBALS, new MapGlobalResolver());

		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				new SyncTestWorkItemHandler());

		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("limit", new Long(1));
		parameters.put("count", new Long(2));

		ProcessInstance process = session.createProcessInstance("test",
				parameters);

		long processInstanceId = process.getId();

		session.startProcessInstance(processInstanceId);
		session.insert(process);
//		session.fireAllRules();
		session.dispose();

		Thread.sleep(5000);

	}

	@Test
	public void test_rule_with_stateless() throws Exception {
		KnowledgeBase kbase = createKnowledgeBase();

		Environment env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());
		env.set(EnvironmentName.GLOBALS, new MapGlobalResolver());

		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		JPAProcessInstanceDbLog log = new JPAProcessInstanceDbLog(env);
		Map<String, Object> parameters = new HashMap<String, Object>();
		parameters.put("limit", new Long(1));
		parameters.put("count", new Long(2));

		ProcessInstance process = session.createProcessInstance(
				"test_stateless", parameters);

		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				new SyncTestWorkItemHandler());
		session.insert(process);
		long processInstanceId = process.getId();

		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				new SyncTestWorkItemHandler());
		session.getWorkItemManager().registerWorkItemHandler("ExceedsRule",
				new StatelessRuleEvaluationWorkItemHandler(kbase));
		session.startProcessInstance(processInstanceId);

		List<VariableInstanceLog> variables = log
				.findVariableInstances(processInstanceId);
		boolean found = false;
		for (Iterator iterator = variables.iterator(); iterator.hasNext();) {
			VariableInstanceLog variableInstanceLog = (VariableInstanceLog) iterator
					.next();
			if (variableInstanceLog.getVariableInstanceId().equalsIgnoreCase(
					"exceeds")) {
				Assert.assertTrue(variableInstanceLog.getValue()
						.equalsIgnoreCase("true"));
				found = true;
			}
			System.out.println(variableInstanceLog);
		}
		Assert.assertTrue(found);

	}
}