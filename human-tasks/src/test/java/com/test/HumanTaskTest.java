package com.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

public class HumanTaskTest extends BaseHumanTaskTest {

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
		return new String[] {"two-tasks-human-task-test.bpmn", "dynamic-user-human-task-test.bpmn"};
	}

	@Override
	protected Map<String, Set<String>> getTestUserGroupsAssignments() {
		Map<String, Set<String>> assign = new HashMap<String, Set<String>>();
		Set<String> user1Groups = new HashSet<String>();
		user1Groups.add("testGroup1");
		user1Groups.add("testGroup2");
		assign.put("testUser1", user1Groups);
		assign.put("testUser2", user1Groups);
		return assign;
	}

	private StatefulKnowledgeSession session;

	@Test
	public void twoHumanTasksCompleted() throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		
		//this will log in audit tables
		new JPAWorkingMemoryDbLogger(session);
		
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		
		//Logger that will give information about the process state, variables, etc
		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(session.getEnvironment());
		
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

		// ended first task, let's take the second..
		List<TaskSummary> tasksUser2 = client.getTasksAssignedAsPotentialOwner(
				"testUser2", "en-UK", groupsUser2);

		this.fullCycleCompleteTask(tasksUser2.get(0).getId(), "testUser2",
				groupsUser2);
		
		//Reload the tasks to see new status.
		tasksUser2 = client.getTasksOwned("testUser2", "en-UK");
		Assert.assertEquals(1, tasksUser2.size());
		Assert.assertEquals(Status.Completed, tasksUser2.get(0).getStatus());
		
		//now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}
	
	@Test
	public void dynamicUser() throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		
		//this will log in audit tables
		new JPAWorkingMemoryDbLogger(session);
		
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		
		//Logger that will give information about the process state, variables, etc
		JPAProcessInstanceDbLog processLog = new JPAProcessInstanceDbLog(session.getEnvironment());
		
		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		Map<String, Object> params = new HashMap<String, Object>();
		params.put("group", "testGroup1");
		ProcessInstance process = session.createProcessInstance("DynamicSimpleTest",
				params);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);
		List<String> groupsUser1 = new ArrayList<String>();
		groupsUser1.add("testGroup1");
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1", "en-UK", groupsUser1);

		Assert.assertEquals(1, tasks.size());
	}

	private void fullCycleCompleteTask(long taskId, String userId,
			List<String> groups) {
		client.claim(taskId, userId, groups);
		client.start(taskId, userId);
		client.complete(taskId, userId, null);

	}

}
