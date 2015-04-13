/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.bpm.engine.impl.bpmn.behavior;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;

/**
 * @author Daniel Meyer
 *
 */
public class ParallelMultiInstanceActivityBehavior extends MultiInstanceActivityBehavior {

  protected void createInstances(ActivityExecution execution, int nrOfInstances) throws Exception {

    // create the concurrent child executions
    List<ActivityExecution> concurrentExecutions = new ArrayList<ActivityExecution>();
    for (int i = 0; i < nrOfInstances; i++) {
      ActivityExecution concurrentChild = execution.createExecution();
      concurrentChild.setConcurrent(true);
      concurrentChild.setScope(false);
      concurrentExecutions.add(concurrentChild);
    }

    // set the MI variables
    setLoopVariable(execution, NUMBER_OF_INSTANCES, nrOfInstances);
    setLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES, 0);
    setLoopVariable(execution, NUMBER_OF_ACTIVE_INSTANCES, nrOfInstances);

    // start the concurrent child executions
    for (int i = 0; i< nrOfInstances; i++) {
      ActivityExecution activityExecution = concurrentExecutions.get(i);
      // check for active execution: the completion condition may be satisfied before all executions are started
      // TODO: should this be a stronger condition? see: OLD_ParallelMultiInstanceBehavior
      if(activityExecution.isActive()) {
        performInstance(activityExecution, i);
      }
    }

    if(!execution.getExecutions().isEmpty()) {
      execution.inactivate();
    }

  }

  public void concurrentChildExecutionEnded(ActivityExecution scopeExecution, ActivityExecution endedExecution) {

    int nrOfCompletedInstances = getLoopVariable(scopeExecution, NUMBER_OF_COMPLETED_INSTANCES) + 1;
    setLoopVariable(scopeExecution, NUMBER_OF_COMPLETED_INSTANCES, nrOfCompletedInstances);
    int nrOfActiveInstances = getLoopVariable(scopeExecution, NUMBER_OF_ACTIVE_INSTANCES) - 1;
    setLoopVariable(scopeExecution, NUMBER_OF_ACTIVE_INSTANCES, nrOfActiveInstances);

    // inactivate the concurrent execution
    endedExecution.inactivate();

    // join
    scopeExecution.forceUpdate();
    // TODO: should the completion condition be evaluated on the scopeExecution or on the endedExecution?
    if(completionConditionSatisfied(endedExecution) ||
        allExecutionsEnded(scopeExecution, endedExecution)) {

      ArrayList<ActivityExecution> childExecutions = new ArrayList<ActivityExecution>(scopeExecution.getExecutions());
      for (ActivityExecution childExecution : childExecutions) {
        ((PvmExecutionImpl)childExecution).deleteCascade("Multi instance completion condition satisfied.");
      }

      scopeExecution.setActive(true);
      leave(scopeExecution);
    }
  }

  protected boolean allExecutionsEnded(ActivityExecution scopeExecution, ActivityExecution endedExecution) {
    return endedExecution.findInactiveConcurrentExecutions(endedExecution.getActivity()).size() == scopeExecution.getExecutions().size();
  }

  public void complete(ActivityExecution scopeExecution) {
    // can't happen
  }

}
