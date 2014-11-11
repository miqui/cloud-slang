package com.hp.score.lang.compiler.utils;/*
 * Licensed to Hewlett-Packard Development Company, L.P. under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
*/

import ch.lambdaj.Lambda;
import com.hp.score.api.ExecutionPlan;
import com.hp.score.lang.compiler.domain.CompiledFlow;
import com.hp.score.lang.compiler.domain.CompiledOperation;
import com.hp.score.lang.compiler.domain.CompiledTask;
import com.hp.score.lang.entities.ScoreLangConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

import static ch.lambdaj.Lambda.having;
import static ch.lambdaj.Lambda.on;
import static org.hamcrest.Matchers.equalTo;

/*
 * Created by orius123 on 11/11/14.
 */
@Component
public class ExecutionPlanBuilder {

    @Autowired
    private ExecutionStepFactory stepFactory;

    private static final String SLANG_NAME = "slang";

    public ExecutionPlan createOperationExecutionPlan(CompiledOperation compiledOperation) {
        ExecutionPlan executionPlan = new ExecutionPlan();
        executionPlan.setLanguage(SLANG_NAME);
        executionPlan.setBeginStep(1L);

        executionPlan.addStep(stepFactory.createStartStep(1L, compiledOperation.getPreExecActionData()));
        executionPlan.addStep(stepFactory.createActionStep(2L, compiledOperation.getCompiledDoAction().getActionData()));
        executionPlan.addStep(stepFactory.createEndStep(3L, compiledOperation.getPostExecActionData()));
        return executionPlan;
    }

    public ExecutionPlan createFlowExecutionPlan(CompiledFlow compiledFlow) {
        ExecutionPlan executionPlan = new ExecutionPlan();
        executionPlan.setLanguage(SLANG_NAME);

        final long FLOW_END_STEP_INDEX = 0L;

        Long stepsIndex = 1L;
        executionPlan.setBeginStep(stepsIndex);
        //flow start step
        executionPlan.addStep(stepFactory.createStartStep(stepsIndex++, compiledFlow.getPreExecActionData()));
        //flow end step
        executionPlan.addStep(stepFactory.createEndStep(FLOW_END_STEP_INDEX, compiledFlow.getPostExecActionData()));

        Map<String, Long> tasksReferences = new HashMap<>();
        tasksReferences.put(ScoreLangConstants.SUCCESS_RESULT, FLOW_END_STEP_INDEX); //todo take actual flow results
        tasksReferences.put(ScoreLangConstants.FAILURE_RESULT, FLOW_END_STEP_INDEX); //todo take actual flow results

        List<CompiledTask> compiledTasks = compiledFlow.getCompiledWorkflow().getCompiledTasks();//todo get only names
        for (CompiledTask compiledTask : compiledTasks) {
            if (tasksReferences.get(compiledTask.getName()) != null) {
                continue;
            }
            stepsIndex = buildTaskExecutionSteps(executionPlan, stepsIndex, tasksReferences, compiledTask, compiledTasks);
        }
//        executionPlan.addSteps(recursivelyBuildTaskExecutionSteps(compiledTasks.get(0), stepsIndex, tasksReferences, dependenciesByNamespace, compiledTasks));

        return executionPlan;
    }

    private Long buildTaskExecutionSteps(ExecutionPlan executionPlan, Long stepsIndex, Map<String, Long> tasksReferences,
                                         CompiledTask compiledTask, List<CompiledTask> compiledTasks) {
        //Begin Task
        tasksReferences.put(compiledTask.getName(), stepsIndex);
        executionPlan.addStep(stepFactory.createBeginTaskStep(stepsIndex++, compiledTask.getPreTaskActionData(), compiledTask.getRefId()));
        Long endTaskIndex = stepsIndex++;

        //End Task
        Map<String, Long> navigationValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : compiledTask.getNavigationStrings().entrySet()) {
            if (tasksReferences.get(entry.getValue()) == null) {
                CompiledTask nextTaskToCompile = Lambda.selectFirst(compiledTasks, having(on(CompiledTask.class).getName(), equalTo(entry.getValue())));
                stepsIndex = buildTaskExecutionSteps(executionPlan, stepsIndex, tasksReferences, nextTaskToCompile, compiledTasks);
            }
            navigationValues.put(entry.getKey(), tasksReferences.get(entry.getValue()));
        }
        executionPlan.addStep(stepFactory.createFinishTaskStep(endTaskIndex, compiledTask.getPostTaskActionData(), navigationValues));
        return stepsIndex;
        //todo do we have tasks that no one ref to?
    }

//
//    private List<ExecutionStep> recursivelyBuildTaskExecutionSteps(CompiledTask compiledTask,
//                                                    Long stepsIndex, Map<String, Long> tasksReferences,
//                                                    TreeMap<String, List<SlangFile>> dependenciesByNamespace, List<CompiledTask> compiledTasks) {
//        List<ExecutionStep> taskExecutionSteps = new ArrayList<>();
//
//        //Begin Task
//        Map<String, Serializable> preTaskActionData = compiledTask.getPreTaskActionData();
//        String refId = resolveRefId(compiledTask.getRefId(), dependenciesByNamespace);
//        tasksReferences.put(compiledTask.getName(), stepsIndex);
//        taskExecutionSteps.add(stepFactory.createBeginTaskStep(stepsIndex++, preTaskActionData, refId));
//
//        //End Task
//        Map<String, Long> navigationValues = new LinkedHashMap<>();
//        for (Map.Entry<String, String> entry : compiledTask.getNavigationStrings().entrySet()) {
//            if (tasksReferences.get(entry.getValue()) == null) {
//                CompiledTask nextTaskToCompile = Lambda.selectFirst(compiledTasks, having(on(CompiledTask.class).getName(), equalTo(entry.getValue())));
//                taskExecutionSteps.addAll(recursivelyBuildTaskExecutionSteps(nextTaskToCompile, stepsIndex, tasksReferences, dependenciesByNamespace, compiledTasks));
//            }
//            navigationValues.put(entry.getKey(), tasksReferences.get(entry.getValue()));
//        }
//        taskExecutionSteps.add(stepFactory.createFinishTaskStep(stepsIndex, compiledTask.getPostTaskActionData(), navigationValues));
//        return taskExecutionSteps;
//    }

}