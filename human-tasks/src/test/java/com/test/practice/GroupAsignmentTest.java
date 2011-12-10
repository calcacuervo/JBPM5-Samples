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
import org.jbpm.process.audit.ProcessInstanceLog;
import org.jbpm.process.audit.VariableInstanceLog;
import org.jbpm.task.Status;
import org.jbpm.task.query.TaskSummary;
import org.jbpm.task.service.hornetq.CommandBasedHornetQWSHumanTaskHandler;
import org.junit.Test;

import com.test.BaseHumanTaskTest;

/**
 * Tests to show particular situations.
 * 
 * @author calcacuervo
 * 
 */
public class GroupAsignmentTest extends BaseHumanTaskTest {

	@Override
	protected String[] getTestUsers() {
		return new String[] { "Administrator", "demian" };
	}

	@Override
	protected String[] getTestGroups() {
		return new String[] { "hr" };
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
		return assign;
	}

	private StatefulKnowledgeSession session;

	@Test
	public void completeSimpleHumanTask() throws InterruptedException {
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
				"SimpleGroupAsignment", null);
		session.insert(process);
		long processInstanceId = process.getId();
		session.startProcessInstance(processInstanceId);
		Thread.sleep(2000);

		// A new task for testUser1 will be created.
		List<TaskSummary> tasks = client.getTasksAssignedAsPotentialOwner(
				"demian", "en-UK",
				this.getTestUserGroupsAssignments().get("demian"));

		Assert.assertEquals(1, tasks.size());

		// complete task.
		Map<String, Object> completeData = new HashMap<String, Object>();
		completeData.put("decision", "approved");
		this.fullCycleCompleteTask(tasks.get(0).getId(), "demian", this
				.getTestUserGroupsAssignments().get("demian"), completeData);

		tasks = client.getTasksOwned("demian", "en-UK");
		Assert.assertEquals(1, tasks.size());
		Assert.assertEquals(Status.Completed, tasks.get(0).getStatus());

		// now check in the logs the process finished.
		ProcessInstanceLog processInstanceLog = processLog
				.findProcessInstance(processInstanceId);
		Assert.assertNotNull(processInstanceLog.getEnd());

		VariableInstanceLog variables = processLog.findVariableInstances(
				processInstanceId, "decision").get(0);
		Assert.assertEquals("approved", variables.getValue());
	}

	private void fullCycleCompleteTask(long taskId, String userId,
			List<String> groups, Map<String, Object> data) {
		client.claim(taskId, userId, groups);
		// CLAIM NEEDED, AS demian IS
		// THE ONLY POTENTIAL OWNER, IT IS ASSIGNED TO GROUP
		client.start(taskId, userId);
		client.complete(taskId, userId, data);
	}
}
