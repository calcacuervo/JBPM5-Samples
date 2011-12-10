package com.test.mina;

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
import org.jbpm.task.service.TaskClient;
import org.junit.Test;

/**
 * Tests to show particular situations.
 * 
 * @author calcacuervo
 * 
 */
public class HumanTaskTest extends BaseMinaHumanTaskTest {
//
//	@Override
//	protected String[] getTestUsers() {
//		return new String[] { "testUser1", "testUser2", "testUser3",
//				"Administrator" };
//	}
//
//	@Override
//	protected String[] getTestGroups() {
//		return new String[] { "testGroup1", "testGroup2" };
//	}
//
//	@Override
//	protected String[] getProcessPaths() {
//		return new String[] { "two-tasks-human-task-test.bpmn",
//				"dynamic-user-human-task-test.bpmn",
//				"simple-human-task-assigned-to-user-test.bpmn",
//				"simple-human-task-test.bpmn"};
//	}
//
//	@Override
//	protected Map<String, List<String>> getTestUserGroupsAssignments() {
//		Map<String, List<String>> assign = new HashMap<String, List<String>>();
//		List<String> user1Groups = new ArrayList<String>();
//		List<String> user2Groups = new ArrayList<String>();
//		user1Groups.add("testGroup1");
//		user2Groups.add("testGroup2");
//		assign.put("testUser1", user1Groups);
//		assign.put("testUser2", user2Groups);
//		return assign;
//	}
//
//	private StatefulKnowledgeSession session;
//
//	/**
//	 * Show a case when there are two human tasks, and these two are started and
//	 * completed, and the process is finished.
//	 * 
//	 * @throws InterruptedException
//	 */
//	@Test
//	public void twoHumanTasksCompleted() throws InterruptedException {
//		KnowledgeBase kbase = this.createKnowledgeBase();
//		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
//				env);
//
//		// this will log in audit tables
//		new JPAWorkingMemoryDbLogger(session);
//
//		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
//
//		// Logger that will give information about the process state, variables,
//		// etc
//		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
//				session.getEnvironment());
//
//		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
//				session);
//		wsHumanTaskHandler.setClient(client.getTaskClient());
//		session.getWorkItemManager().registerWorkItemHandler("Human Task",
//				wsHumanTaskHandler);
//		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
//				null);
//		session.insert(process);
//		long processInstanceId = process.getId();
//		session.startProcessInstance(processInstanceId);
//		Thread.sleep(2000);
//
//		// A new task for testUser1 will be created.
//		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
//				"testUser1", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser1"));
//
//		Assert.assertEquals(1, tasks.size());
//
//		// complete task.
//		this.fullCycleCompleteTask(tasks.get(0).getId(), "testUser1", this
//				.getTestUserGroupsAssignments().get("testUser1"));
//
//		tasks = client.getTasksOwned("testUser1", "en-UK");
//		Assert.assertEquals(1, tasks.size());
//		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());
//
//		// ended first task, let's take the second.. this will be testUser2
//		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
//				"testUser2", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser2"));
//
//		this.fullCycleCompleteTask(tasksUser2.get(0).getId(), "testUser2", this
//				.getTestUserGroupsAssignments().get("testUser2"));
//
//		// Reload the tasks to see new status.
//		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
//		Assert.assertEquals(1, tasksUser2.size());
//		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());
//
//		// now check in the logs the process finished.
//		ProcessInstanceLog processInstanceLog = processLog
//				.findProcessInstance(processInstanceId);
//		Assert.assertNotNull(processInstanceLog.getEnd());
//	}
//
//	/**
//	 * Test to show how the a user or group can be dinamically selected in the
//	 * bpmn file. Please refer to this process:
//	 * dynamic-user-human-task-test.bpmn to see how to declare the groupId or
//	 * actorId to be picked up from a process variable.
//	 * 
//	 * @throws InterruptedException
//	 */
//	@Test
//	public void dynamicUser() throws InterruptedException {
//		KnowledgeBase kbase = this.createKnowledgeBase();
//		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
//				env);
//
//		// this will log in audit tables
//		new JPAWorkingMemoryDbLogger(session);
//
//		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
//
//		// Logger that will give information about the process state, variables,
//		// etc
//		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
//				session.getEnvironment());
//
//		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
//				session);
//		wsHumanTaskHandler.setClient(client.getTaskClient());
//		session.getWorkItemManager().registerWorkItemHandler("Human Task",
//				wsHumanTaskHandler);
//		Map<String, Object> params = new HashMap<String, Object>();
//
//		// this variable "group" will be used to assign the task.
//		params.put("group", "testGroup2");
//		ProcessInstance process = session.createProcessInstance(
//				"DynamicSimpleTest", params);
//		session.insert(process);
//		long processInstanceId = process.getId();
//		session.startProcessInstance(processInstanceId);
//		Thread.sleep(2000);
//
//		// Check a task was created according to the variable group
//		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
//				"testUser2", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser2"));
//
//		Assert.assertEquals(1, tasks.size());
//	}
//
//	/**
//	 * Test for skipping a human task.
//	 * 
//	 * @throws InterruptedException
//	 */
//	@Test
//	public void skipHumanTask() throws InterruptedException {
//		KnowledgeBase kbase = this.createKnowledgeBase();
//		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
//				env);
//
//		// this will log in audit tables
//		new JPAWorkingMemoryDbLogger(session);
//
//		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
//
//		// Logger that will give information about the process state, variables,
//		// etc
//		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
//				session.getEnvironment());
//
//		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
//				session);
//		wsHumanTaskHandler.setClient(client.getTaskClient());
//		session.getWorkItemManager().registerWorkItemHandler("Human Task",
//				wsHumanTaskHandler);
//		ProcessInstance process = session.createProcessInstance("TwoTasksTest",
//				null);
//		session.insert(process);
//		long processInstanceId = process.getId();
//		session.startProcessInstance(processInstanceId);
//		Thread.sleep(2000);
//
//		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
//				"testUser1", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser1"));
//
//		Assert.assertEquals(1, tasks.size());
//		long taskId = tasks.get(0).getId();
//
//		client.claim(taskId, "testUser1", this.getTestUserGroupsAssignments()
//				.get("testUser1"));
//		client.start(taskId, "testUser1");
//
//		// Here we skip the In Progress task-
//		client.skip(taskId, "testUser1");
//
//		tasks = client.getTasksOwned("testUser1", "en-UK");
//		Assert.assertEquals(1, tasks.size());
//		Assert.assertEquals(Status.Obsolete, tasks.get(0).getStatus());
//
//		// And then the process continues.
//		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
//				"testUser2", "en-UK",
//				this.getTestUserGroupsAssignments().get("testUser2"));
//
//		this.fullCycleCompleteTask(tasksUser2.get(0).getId(), "testUser2", this
//				.getTestUserGroupsAssignments().get("testUser2"));
//
//		// Reload the tasks to see new status.
//		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
//		Assert.assertEquals(1, tasksUser2.size());
//		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());
//
//		// now check in the logs the process finished.
//		ProcessInstanceLog processInstanceLog = processLog
//				.findProcessInstance(processInstanceId);
//		Assert.assertNotNull(processInstanceLog.getEnd());
//	}
//
//	/**
//	 * I have almost all tests with tasks potentially owned by groups. This test
//	 * uses the
//	 * {@link TaskClient#getTasksAssignedAsPotentialOwner(String, String, org.jbpm.task.service.TaskClientHandler.TaskSummaryResponseHandler)}
//	 * method.
//	 * 
//	 * @throws InterruptedException
//	 */
//	@Test
//	public void humanTaskAssignedToUser() throws InterruptedException {
//		KnowledgeBase kbase = this.createKnowledgeBase();
//		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
//				env);
//
//		// this will log in audit tables
//		new JPAWorkingMemoryDbLogger(session);
//
//		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
//
//		// Logger that will give information about the process state, variables,
//		// etc
//		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(
//				session.getEnvironment());
//
//		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
//				session);
//		wsHumanTaskHandler.setClient(client.getTaskClient());
//		session.getWorkItemManager().registerWorkItemHandler("Human Task",
//				wsHumanTaskHandler);
//		Map<String, Object> vars = new HashMap<String, Object>();
//		vars.put("testVar", "A value");
//		ProcessInstance process = session.createProcessInstance(
//				"HumanTaskAssignedToUser", null);
//		session.insert(process);
//		long processInstanceId = process.getId();
//		session.startProcessInstance(processInstanceId);
//		Thread.sleep(2000);
//
//		// A new task for testUser1 will be created.
//		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
//				"testUser1", "en-UK", null);
//
//		Assert.assertEquals(1, tasks.size());
//
//		// complete task. As it is automatically put in reserved, we don't have to claim it.
//		Map<String, Object> params = (Map<String, Object>) client.getTaskContent(tasks.get(0).getId());
//		Assert.assertEquals("A value", params.get("testVar"));
//		Assert.assertEquals(Status.Reserved, tasks.get(0).getStatus());
//		client.start(tasks.get(0).getId(), "testUser1");
//		client.complete(tasks.get(0).getId(), "testUser1", null);
//		
//		tasks = client.getTasksOwned("testUser1", "en-UK");
//		Assert.assertEquals(1, tasks.size());
//		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());
//
//		// now check in the logs the process finished.
//		ProcessInstanceLog processInstanceLog = processLog
//				.findProcessInstance(processInstanceId);
//		Assert.assertNotNull(processInstanceLog.getEnd());
//	}
//
//	private void fullCycleCompleteTask(long taskId, String userId,
//			List<String> groups) {
//		client.claim(taskId, userId, groups);
//		client.start(taskId, userId);
//		client.complete(taskId, userId, null);
//	}
}
