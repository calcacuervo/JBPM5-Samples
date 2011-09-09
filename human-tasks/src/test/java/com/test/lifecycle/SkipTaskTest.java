package com.test.lifecycle;

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
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.workitem.wsht.CommandBasedWSHumanTaskHandler;
import org.jbpm.task.Status;
import org.jbpm.task.query.TaskSummary;
import org.junit.Test;

import com.test.BaseHumanTaskTest;

/**
 * This test will show how to use skip operation of tasks......................
 * A person working on a human task or a business administrator may decide that
 * a task is no longer needed, and hence skip this task. This transitions the
 * task into the Obsolete state. This is considered a “good” outcome of a task,
 * even though an empty result is returned. The enclosing environment can be
 * notified of that transition as described in section 7. The task can only be
 * skipped if this capability is specified during the task invocation. A
 * side-effect of this is that a task which is invoked using basic Web service
 * protocols is not skipable.
 * 
 * @author calcacuervo
 * 
 */
public class SkipTaskTest extends BaseHumanTaskTest {

	@Override
	protected String[] getTestUsers() {
		return new String[] { "testUser1", "testUser2", "testUser3",
				"Administrator" };
	}

	@Override
	protected String[] getTestGroups() {
		return new String[] { "testGroup1", "testGroup2" };
	}

	@Override
	protected String[] getProcessPaths() {
		return new String[] { "two-tasks-human-task-test.bpmn",
				"dynamic-user-human-task-test.bpmn" };
	}

	@Override
	protected Map<String, List<String>> getTestUserGroupsAssignments() {
		Map<String, List<String>> assign = new HashMap<String, List<String>>();
		List<String> user1Groups = new ArrayList<String>();
		List<String> user2Groups = new ArrayList<String>();
		user1Groups.add("testGroup1");
		user2Groups.add("testGroup2");
		assign.put("testUser1", user1Groups);
		assign.put("testUser2", user2Groups);
		return assign;
	}

	private StatefulKnowledgeSession session;

	/**
	 * Remove a task which is already In Progress.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void skipInProgressHumanTask() throws InterruptedException {
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

		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser1"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments()
				.get("testUser1"));
		client.start(taskId, "testUser1");

		// Here we skip the In Progress task-
		client.skip(taskId, "testUser1");

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Obsolete, tasks.get(0).getStatus());

		// And then the process continues.
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		taskId = tasksUser2.get(0).getId();
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}
	
	/**
	 * Remove a task which is already claimed.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void skipClaimedHumanTask() throws InterruptedException {
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

		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser1"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments()
				.get("testUser1"));

		// Here we skip the In Progress task-
		client.skip(taskId, "testUser1");

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Obsolete, tasks.get(0).getStatus());

		// And then the process continues.
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		taskId = tasksUser2.get(0).getId();
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}
	
	/**
	 * Remove a task which is unclaimed.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void skipUnclaimedHumanTask() throws InterruptedException {
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

		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser1"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		// Here we skip the unclaimed task.
		client.skip(taskId, "Administrator");
		
		Thread.sleep(1000);

		// And then the process continues.
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		taskId = tasksUser2.get(0).getId();
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}


}
