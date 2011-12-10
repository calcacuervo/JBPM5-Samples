package com.test.practice;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.drools.KnowledgeBase;
import org.drools.logger.KnowledgeRuntimeLoggerFactory;
import org.drools.persistence.jpa.JPAKnowledgeService;
import org.drools.runtime.StatefulKnowledgeSession;
import org.drools.runtime.process.ProcessInstance;
import org.jbpm.process.audit.JPAProcessInstanceDbLog;
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.hornetq.CommandBasedHornetQWSHumanTaskHandler;
import org.junit.Test;

import com.test.BaseHumanTaskTest;

/**
 * @author calcacuervo
 * 
 */
public class QueryTasksTest extends BaseHumanTaskTest {

	@Override
	protected String[] getTestUsers() {
		return new String[] { "demian", "john", "demian's friend",
				"Administrator" };
	}

	@Override
	protected String[] getTestGroups() {
		return new String[] { "hr", "dev" };
	}

	@Override
	protected String[] getProcessPaths() {
		return new String[] { "com/test/practice/04-multiple-task.bpmn" };
	}

	@Override
	protected Map<String, List<String>> getTestUserGroupsAssignments() {
		Map<String, List<String>> assign = new HashMap<String, List<String>>();
		List<String> user1Groups = new ArrayList<String>();
		user1Groups.add("hr");
		assign.put("demian", user1Groups);
		assign.put("john", user1Groups);
		return assign;
	}

	private StatefulKnowledgeSession session;

	/**
	 * This test shows how to the owner of a task, who already claims it, can
	 * delegate it.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void actualOwnerDelegatesHisClaimedTask()
			throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);

		// this will log in audit tables
		new JPAWorkingMemoryDbLogger(session);

		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);

		// Logger that will give information about the process state, variables,
		// etc
		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
				session.getEnvironment());

		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance(
				"MultipleTasks", null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"demian", "en-UK",
				this.getTestUserGroupsAssignments().get("demian"));

		Assert.assertEquals(1, tasks.size());

		long taskId = tasks.get(0).getId();
		// demian claims his task. After that, he is the actual owner
		client.claim(taskId, "demian",
				this.getTestUserGroupsAssignments().get("demian"));

		client.start(taskId, "demian");
		client.complete(taskId, "demian", null);

		// let's start two more instances
		session.startProcess("MultipleTasks");
		session.startProcess("MultipleTasks");

		// now, there should be two tasks for Review CV and one for Technical
		// Review.
		// let's check it using a query...
		List count = this.client
				.query("select count(t) from Task t left join t.names as name where name.text = 'Review CV' and t.taskData.status in ('Ready', 'Reserved')",
						Integer.MAX_VALUE);
		Assert.assertEquals(new Long(2), count.get(0));
		count = this.client
				.query("select count(t) from Task t left join t.names as name where name.text = 'Review CV' and t.taskData.status in ('Completed')",
						Integer.MAX_VALUE);
		Assert.assertEquals(new Long(1), count.get(0));
		count = this.client
				.query("select count(t) from Task t left join t.names as name where name.text = 'Technical Review' and t.taskData.status in ('Ready', 'Reserved')",
						Integer.MAX_VALUE);
		Assert.assertEquals(new Long(1), count.get(0));
	}

}
