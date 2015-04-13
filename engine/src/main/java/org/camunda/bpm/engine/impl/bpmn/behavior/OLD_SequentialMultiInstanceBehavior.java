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

import java.util.List;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.delegate.BpmnError;
import org.camunda.bpm.engine.impl.bpmn.parser.BpmnParse;
import org.camunda.bpm.engine.impl.core.variable.scope.AbstractVariableScope;
import org.camunda.bpm.engine.impl.jobexecutor.TimerDeclarationImpl;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityBehavior;
import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;
import org.camunda.bpm.engine.impl.pvm.runtime.PvmExecutionImpl;


/**
 * @author Joram Barrez
 * @author Falko Menge
 * @author Ronny Bräunlich
 */
public class OLD_SequentialMultiInstanceBehavior extends OLD_MultiInstanceActivityBehavior {

  public OLD_SequentialMultiInstanceBehavior(ActivityImpl activity, AbstractBpmnActivityBehavior innerActivityBehavior) {
    super(activity, innerActivityBehavior);
  }

  /**
   * Handles the sequential case of spawning the instances.
   * Will only create one instance, since at most one instance can be active.
   */
  protected void createInstances(ActivityExecution execution, int nrOfInstances) throws Exception {
    setLoopVariable(execution, NUMBER_OF_INSTANCES, nrOfInstances);
    setLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES, 0);
    setLoopVariable(execution, LOOP_COUNTER, 0);
    setLoopVariable(execution, NUMBER_OF_ACTIVE_INSTANCES, 1);
    logLoopDetails(execution, "initialized", 0, 0, 1, nrOfInstances);

    executeIoMapping((AbstractVariableScope) execution);

    executeOriginalBehavior(execution, 0);
  }

  /**
   * Called when the wrapped {@link ActivityBehavior} calls the
   * {@link AbstractBpmnActivityBehavior#leave(ActivityExecution)} method.
   * Handles the completion of one instance, and executes the logic for the sequential behavior.
   */
  public void leave(ActivityExecution execution) {

    int loopCounter = getLoopVariable(execution, LOOP_COUNTER) + 1;
    int nrOfInstances = getLoopVariable(execution, NUMBER_OF_INSTANCES);
    int nrOfCompletedInstances = getLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES) + 1;
    int nrOfActiveInstances = getLoopVariable(execution, NUMBER_OF_ACTIVE_INSTANCES);

    setLoopVariable(execution, LOOP_COUNTER, loopCounter);
    setLoopVariable(execution, NUMBER_OF_COMPLETED_INSTANCES, nrOfCompletedInstances);
    logLoopDetails(execution, "instance completed", loopCounter, nrOfCompletedInstances, nrOfActiveInstances, nrOfInstances);

    if (loopCounter == nrOfInstances || completionConditionSatisfied(execution)) {
      super.leave(execution); // last instance
    } else {
//      for (EventSubscriptionDeclaration declaration : EventSubscriptionDeclaration.getDeclarationsForScope(execution.getActivity())) {
//        declaration.handleSequentialMultiInstanceLeave((ExecutionEntity) execution);
//      }

      // the given execution will not be leaved in that case
      // that's why we have to increment the sequence counter
      ((PvmExecutionImpl) execution).incrementSequenceCounter();

      deleteExistingJobs(execution);
      callActivityEndListeners(execution);
      executeIoMapping((AbstractVariableScope) execution);
      createTimerJobsForExecution(execution);

      try {
        executeOriginalBehavior(execution, loopCounter);
      } catch (BpmnError error) {
        // re-throw business fault so that it can be caught by an Error Intermediate Event or Error Event Sub-Process in the process
        throw error;
      } catch (Exception e) {
        throw new ProcessEngineException("Could not execute inner activity behavior of multi instance behavior", e);
      }
    }
  }

  @Override
  protected void doExecuteOriginalBehavior(ActivityExecution execution, int loopCounter) throws Exception {
    // If loopcounter == 0, then historic activity instance already created, no need to
    // pass through executeActivity again since it will create a new historic activity
    if (loopCounter == 0) {
      innerActivityBehavior.execute(execution);
    } else {
      execution.executeActivity(activity);
    }
  }

  @SuppressWarnings("unchecked")
  protected void createTimerJobsForExecution(ActivityExecution execution) {
    List<TimerDeclarationImpl> timerDeclarations = (List<TimerDeclarationImpl>) execution.getActivity().getProperty(BpmnParse.PROPERTYNAME_TIMER_DECLARATION);
    if (timerDeclarations!=null) {
      for (TimerDeclarationImpl timerDeclaration : timerDeclarations) {
        timerDeclaration.createTimerInstance((ExecutionEntity) execution);
      }
    }
  }

  protected void deleteExistingJobs(ActivityExecution execution) {
    List<JobEntity> jobs = ((ExecutionEntity)execution).getJobs();
    for (JobEntity jobEntity : jobs) {
      jobEntity.delete();
    }
  }

  @Override
  public void execute(ActivityExecution execution) throws Exception {
    super.execute(execution);

    if(innerActivityBehavior instanceof SubProcessActivityBehavior) {
      // ACT-1185: end-event in subprocess may have inactivated execution
      if(!execution.isActive() && execution.isEnded() && (execution.getExecutions() == null || execution.getExecutions().size() == 0)) {
        execution.setActive(true);
      }
    }
  }

  /* (non-Javadoc)
   * @see org.camunda.bpm.engine.impl.pvm.delegate.CompositeActivityBehavior#concurrentChildExecutionEnded(org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution, org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution)
   */
  @Override
  public void concurrentChildExecutionEnded(ActivityExecution scopeExecution, ActivityExecution endedExecution) {
    // TODO Auto-generated method stub

  }

  /* (non-Javadoc)
   * @see org.camunda.bpm.engine.impl.pvm.delegate.CompositeActivityBehavior#complete(org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution)
   */
  @Override
  public void complete(ActivityExecution scopeExecution) {
    // TODO Auto-generated method stub

  }

}
