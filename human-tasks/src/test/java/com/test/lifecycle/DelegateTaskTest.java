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
import org.jbpm.task.service.hornetq.CommandBasedHornetQWSHumanTaskHandler;
import org.junit.Test;

import com.test.BaseHumanTaskTest;

/**
 * This test will show how to delegate tasks to another user.................
 * Task’s potential owners, actual owner or business administrator can delegate
 * a task to another user, making that user the actual owner of the task, and
 * also adding her to the list of potential owners in case she is not, yet. A
 * task can be delegated when it is in an active state (Ready, Reserved,
 * InProgress), and transitions the task into the Reserved state. Business data
 * associated with the task is kept. Similarily, task’s potential owners, actual
 * owner or business administrator can forward an active task to another person
 * or a set of people, replacing himself by those people in the list of
 * potential owners. Potential owners can only forward tasks that are in the
 * Ready state. Forwarding is possible if the task has a set of individually
 * assigned potential owners, not if its potential owners are assigned using one
 * or many groups. If the task is in the Reserved or InProgress state then the
 * task is implicitly released first, that is, the task is transitioned into the
 * Ready state. Business data associated with the task is kept. The user
 * performing the forward is removed from the set of potential owners of the
 * task, and the forwardee is added to the set of potential owners.
 * 
 * @author calcacuervo
 * 
 */
public class DelegateTaskTest extends BaseHumanTaskTest {

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
		// testUser1 claims his task. After that, he is the actual owner
		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments()
				.get("testUser1"));

		// now, testUser1, delegates the task to testUser2
		client.delegate(taskId, "testUser1", "testUser2");

		// testUser2 will have the task assigned
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(taskId, tasksUser2.get(0).getId());
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// Ok, now the flow continues for the next task.
		tasksUser2 = client.getTasksAssignedAsPotentialOwner("testUser2",
				"en-UK", this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		client.claim(tasksUser2.get(0).getId(), "testUser2", this
				.getTestUserGroupsAssignments().get("testUser2"));
		client.start(tasksUser2.get(0).getId(), "testUser2");
		client.complete(tasksUser2.get(0).getId(), "testUser2", null);

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}

	/**
	 * This test shows how the Administrator can delegate a claimed task.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void administratorDelegatesClaimedTask() throws InterruptedException {
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
		// testUser1 claims his task. After that, he is the actual owner
		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments()
				.get("testUser1"));

		// now, the Administrator, delegates the task to testUser2
		client.delegate(taskId, "Administrator", "testUser2");

		// testUser2 will have the task assigned
		// ended first task, let's take the second..
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(taskId, tasksUser2.get(0).getId());
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// Ok, now the flow continues for the next task.
		tasksUser2 = client.getTasksAssignedAsPotentialOwner("testUser2",
				"en-UK", this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		client.claim(tasksUser2.get(0).getId(), "testUser2", this
				.getTestUserGroupsAssignments().get("testUser2"));
		client.start(tasksUser2.get(0).getId(), "testUser2");
		client.complete(tasksUser2.get(0).getId(), "testUser2", null);

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}

	/**
	 * This test shows how the Administrator can delegate an unclaimed task.
	 * 
	 * @throws InterruptedException
	 */
	@Test
	public void administratorDelegatesUnclaimedTask()
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

		// now, the Administrator, delegates the task to testUser2
		client.delegate(taskId, "Administrator", "testUser2");

		// testUser2 will have the task assigned
		// ended first task, let's take the second..
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK",
				this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(taskId, tasksUser2.get(0).getId());
		client.claim(taskId, "testUser2", this.getTestUserGroupsAssignments()
				.get("testUser2"));
		client.start(taskId, "testUser2");
		client.complete(taskId, "testUser2", null);

		// Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());

		// Ok, now the flow continues for the next task.
		tasksUser2 = client.getTasksAssignedAsPotentialOwner("testUser2",
				"en-UK", this.getTestUserGroupsAssignments().get("testUser2"));

		Assert.assertEquals(1, tasksUser2.size());
		client.claim(tasksUser2.get(0).getId(), "testUser2", this
				.getTestUserGroupsAssignments().get("testUser2"));
		client.start(tasksUser2.get(0).getId(), "testUser2");
		client.complete(tasksUser2.get(0).getId(), "testUser2", null);

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}

}
