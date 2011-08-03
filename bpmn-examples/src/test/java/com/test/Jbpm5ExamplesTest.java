package com.test;

import java.util.HashMap;
import java.util.Map;

import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.KnowledgeBaseFactory;
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
import org.drools.runtime.process.WorkflowProcessInstance;
import org.jbpm.bpmn2.handler.ServiceTaskHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import bitronix.tm.TransactionManagerServices;
import bitronix.tm.resource.jdbc.PoolingDataSource;

public class Jbpm5ExamplesTest {

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
		kbuilder.add(new ClassPathResource("com/BPMN2-ServiceProcess.bpmn2"),
				ResourceType.BPMN2);

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

	/**
	 * Tests a service task node with POJOS.
	 * @throws Exception
	 */
	@Test
	public void testServiceTask() throws Exception {
		KnowledgeBase kbase = createKnowledgeBase();
		Environment env = KnowledgeBaseFactory.newEnvironment();
		env.set(EnvironmentName.ENTITY_MANAGER_FACTORY, emf);
		env.set(EnvironmentName.TRANSACTION_MANAGER,
				TransactionManagerServices.getTransactionManager());

		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);

		session.getWorkItemManager().registerWorkItemHandler("Service Task",
				new ServiceTaskHandler());
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("s", "john");
		DataInput input = new DataInput(params);
		Map<String, Object> params2 = new HashMap<String, Object>();
		params2.put("s", input);
		WorkflowProcessInstance processInstance = (WorkflowProcessInstance) session
				.startProcess("ServiceProcess", params2);
		Assert.assertEquals("Hello john!", ((DataOutput)processInstance.getVariable("s")).getDataMap().get("s"));
	}
	}