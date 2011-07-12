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
import org.jbpm.process.audit.JPAWorkingMemoryDbLogger;
import org.jbpm.process.workitem.wsht.CommandBasedWSHumanTaskHandler;
import org.jbpm.task.Status;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.PermissionDeniedException;
import org.junit.Test;

public class HumanTaskGroupTest extends BaseHumanTaskTest {

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
		return new String[] { "simple-human-task-test.bpmn" };
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
	public void withoutAddingUsers() throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleTest",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);
		List<String> groups = new ArrayList<String>();
		groups.add("testGroup1");
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"testUser1","en-UK", groups);

		Assert.assertEquals(1, tasks.size());


		client.claim(tasks.get(0).getId(), "testUser1", groups);

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());

		client.start(tasks.get(0).getId(), "testUser1");

		client.complete(tasks.get(0).getId(), "testUser1", null);

		tasks = client.getTasksOwned("testUser1", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());
	}

	
	@Test(expected = PermissionDeniedException.class)
	public void unauthorizedAttempt() throws InterruptedException {
		KnowledgeBase kbase = this.createKnowledgeBase();
		session = JPAKnowledgeService.newStatefulKnowledgeSession(kbase, null,
				env);
		new JPAWorkingMemoryDbLogger(session);
		KnowledgeRuntimeLoggerFactory.newConsoleLogger(session);
		CommandBasedWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleTest",
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
				"testUser2", "en-UK", groupsUser2);

		Assert.assertEquals(0, tasks.size());

		// OK, the user was no assignments, but will try to access to an
		// authorized id.
		tasks = client.getTasksAssignedAsPotentialOwner("testUser1", "en-UK", groupsUser1);

		Assert.assertEquals(1, tasks.size());

		client.claim(tasks.get(0).getId(), "testUser2", groupsUser2);

	}
}
