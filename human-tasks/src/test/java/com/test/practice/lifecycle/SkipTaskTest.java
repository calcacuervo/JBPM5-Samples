package com.test.practice.lifecycle;

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
		return new String[] { "demian", "john", "demian's friend",
				"Administrator" };
	}

	@Override
	protected String[] getTestGroups() {
		return new String[] { "hr", "dev" };
	}

	@Override
	protected String[] getProcessPaths() {
		return new String[] { "com/test/practice/02-simple-group-asignment.bpmn" };
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

		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleGroupAsignment",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"demian", "en-UK",
				this.getTestUserGroupsAssignments().get("demian"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		client.claim(taskId, "demian", this.getTestUserGroupsAssignments()
				.get("demian"));
		client.start(taskId, "demian");

		// Here we skip the In Progress task-
		client.skip(taskId, "demian");

		tasks = client.getTasksOwned("demian", "en-UK");
		Assert.assertEquals(1, tasks.size());
		//The task is put as Obsolete!
		Assert.assertEquals(Status.Obsolete, tasks.get(0).getStatus());

		// And then the process continues.
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

		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleGroupAsignment",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"demian", "en-UK",
				this.getTestUserGroupsAssignments().get("demian"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		client.claim(taskId, "demian", this.getTestUserGroupsAssignments()
				.get("demian"));

		// Here we skip the In Progress task-
		client.skip(taskId, "demian");

		tasks = client.getTasksOwned("demian", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Obsolete, tasks.get(0).getStatus());

		// And then the process continues.
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

		CommandBasedHornetQWSHumanTaskHandler wsHumanTaskHandler = new CommandBasedHornetQWSHumanTaskHandler(
				session);
		wsHumanTaskHandler.setClient(client.getTaskClient());
		session.getWorkItemManager().registerWorkItemHandler("Human Task",
				wsHumanTaskHandler);
		ProcessInstance process = session.createProcessInstance("SimpleGroupAsignment",
				null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"demian", "en-UK",
				this.getTestUserGroupsAssignments().get("demian"));

		Assert.assertEquals(1, tasks.size());
		long taskId = tasks.get(0).getId();

		// Here the administrator skip the unclaimed task.
		client.skip(taskId, "Administrator");

		// And then the process continues.
		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());
	}

}
