package com.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.drools.KnowledgeBase;
import org.drools.audit.WorkingMemoryConsoleLogger;
import org.drools.runtime.StatelessKnowledgeSession;
import org.drools.runtime.process.WorkItem;
import org.drools.runtime.process.WorkItemHandler;
import org.drools.runtime.process.WorkItemManager;

/**
 * Work item that will be used to evaluate rules which are stateless by nature,
 * such as validations.
 * 
 */
public class StatelessRuleEvaluationWorkItemHandler implements WorkItemHandler {

	/**
	 * The stateless session that will be used in this work item.
	 */
	private StatelessKnowledgeSession ksession;

	/**
	 * Creates a new {@link StatelessRuleEvaluationWorkItemHandler} instance. It
	 * will create a new stateless session using the given {@link KnowledgeBase}
	 * .
	 * 
	 * @param kbase
	 */
	public StatelessRuleEvaluationWorkItemHandler(KnowledgeBase kbase) {
		ksession = kbase.newStatelessKnowledgeSession();
		new WorkingMemoryConsoleLogger(ksession);
	}

	/**
	 * Executes the work item. It will take the work item parameters, and call
	 * the stateless session to evaluate the rules. The rule will also receive
	 * an instance of {@link DataOutput}, which internally has a map. The rule
	 * will be responsible to fill in this map with the desired results. The
	 * variables in this map will be put in the process context (if mapped!).
	 */
	@Override
	public void executeWorkItem(WorkItem workItem, WorkItemManager manager) {
		List<Object> facts = new ArrayList<Object>();
		facts.add(new DataInput(workItem.getParameters()));
		DataOutput output = new DataOutput(new HashMap<String, Object>());
		facts.add(output);
		ksession.execute(facts);
		manager.completeWorkItem(workItem.getId(), output.getDataMap());
	}

	@Override
	public void abortWorkItem(WorkItem workItem, WorkItemManager manager) {
		// TODO fill this.
	}

	
}
