import { describe, expect, it } from 'vitest';
import { initialWorkflowState, workflowReducer } from './workflowStore';

describe('workflowReducer', () => {
  it('adds user and assistant messages', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'hello', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });

    expect(state.messages.length).toBe(2);
    expect(state.messages[0].role).toBe('user');
    expect(state.messages[1].role).toBe('assistant');
  });

  it('handles result and marks idle', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'task', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_RESULT',
      payload: { result: 'done' }
    });

    expect(state.messages[1].content).toBe('done');
    expect(state.messages[1].thoughtSteps[state.messages[1].thoughtSteps.length - 1]?.kind).toBe('result');
    expect(state.loading).toBe(false);
    expect(state.connectionStatus).toBe('idle');
  });

  it('collects tool output and error', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: { eventType: 'ERROR', agentName: 'agent', output: 'x', error: 'boom' }
    });

    expect(state.error).toBe('boom');
    expect(state.executionTimeline[0]?.kind).toBe('error');
  });

  it('surfaces key execution events in tool output panel', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: { eventType: 'AGENT_START', agentName: 'execution_coordinator', input: 'hello' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: { eventType: 'LLM_RESPONSE', agentName: 'llm', output: '{"content":"ok"}' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_START',
        agentName: 'search_web',
        input: '{"query":"openai"}',
        metadata: { toolName: 'search_web' }
      }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_END',
        agentName: 'search_web',
        output: '搜索结果: openai',
        metadata: { toolName: 'search_web' }
      }
    });

    expect(state.toolOutputs.map((item) => item.type)).toEqual([
      'search_web 结果',
      'search_web 参数',
      'AI Message',
      '用户请求'
    ]);
    expect(state.toolOutputs[0].content).toContain('搜索结果');
  });

  it('surfaces intermediate agent team coding stages in trace and tool output', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'build feature', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'INTERMEDIATE_RESULT',
        agentName: 'agentteam_coding_coordinator',
        output: 'integrationBranch=agentteam/integration-123',
        metadata: {
          stage: 'INTEGRATION',
          integrationBranch: 'agentteam/integration-123',
          verification: 'compile success'
        }
      }
    });

    expect(state.messages[1].thoughtSteps[0]?.kind).toBe('status');
    expect(state.messages[1].thoughtSteps[0]?.title).toBe('Agent Team INTEGRATION');
    expect(state.toolOutputs[0]?.type).toBe('Agent Team INTEGRATION');
    expect(state.toolOutputs[0]?.content).toContain('integrationBranch=agentteam/integration-123');
  });

  it('stores structured team coding metadata for frontend inspection', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'INTERMEDIATE_RESULT',
        agentName: 'agentteam_coding_coordinator',
        output: 'parallel coding summary',
        metadata: {
          stage: 'SUMMARY',
          groupId: 'coding-group-1',
          repositoryPath: 'E:/Project/OpenManus-Java',
          taskCount: 1,
          success: true,
          tasks: [
            {
              taskId: 'task-a',
              title: 'API task',
              goal: 'Implement endpoint',
              ownedPaths: ['src/main/java/api'],
              verificationCommands: ['./scripts/mvnw-local.sh test'],
              conflictRisk: 'low'
            }
          ],
          subAgents: [
            {
              taskId: 'task-a',
              status: 'SUCCEEDED',
              summary: 'done',
              branchName: 'agentteam/task-a',
              commitSha: 'abc123',
              worktreePath: 'E:/wt/task-a',
              changedFiles: ['A.java'],
              testPassed: true,
              testSummary: 'tests passed',
              errorMessage: ''
            }
          ]
        }
      }
    });

    expect(state.agentTeamCoding?.stage).toBe('SUMMARY');
    expect(state.agentTeamCoding?.groupId).toBe('coding-group-1');
    expect(state.agentTeamCoding?.subTasks[0]?.branchName).toBe('agentteam/task-a');
    expect(state.agentTeamCoding?.subTasks[0]?.commitSha).toBe('abc123');
    expect(state.agentTeamCoding?.subTasks[0]?.ownedPaths).toEqual(['src/main/java/api']);
  });

  it('updates browser state from structured search events', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'SEARCH_STARTED',
        metadata: {
          query: 'openai docs',
          searchPageUrl: 'https://www.google.com/search?q=openai+docs',
          previewMode: 'web'
        }
      }
    });

    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'SEARCH_RESULTS_READY',
        metadata: {
          resultItems: [
            {
              title: 'OpenAI Docs',
              url: 'https://platform.openai.com/docs',
              snippet: 'Official docs'
            }
          ]
        }
      }
    });

    expect(state.searchQuery).toBe('openai docs');
    expect(state.browserStatus).toBe('results-ready');
    expect(state.currentUrl).toBe('');
    expect(state.searchResults[0].url).toBe('https://platform.openai.com/docs');
    expect(state.searchTimeline.length).toBeGreaterThan(0);
    expect(state.webTimeline).toHaveLength(0);
  });

  it('marks auto switch vnc when preview is blocked and sandbox exists', () => {
    const state = workflowReducer({
      ...initialWorkflowState,
      sandboxVncUrl: 'https://vnc.local'
    }, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'WEB_PREVIEW_BLOCKED',
        metadata: {
          activeUrl: 'https://blocked.example.com',
          previewMode: 'vnc',
          blockReason: 'fetch-failed',
          detail: '代理抓取网页失败',
          autoSwitchedToVnc: true
        }
      }
    });

    expect(state.browserStatus).toBe('auto-switch-vnc');
    expect(state.autoSwitchedToVnc).toBe(true);
    expect(state.previewBlockedReason).toContain('fetch-failed');
  });

  it('stores vnc url when browser opened event comes from real browser', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'BROWSER_URL_OPENED',
        metadata: {
          activeUrl: 'https://example.com',
          previewMode: 'vnc',
          sandboxVncUrl: 'https://vnc.local'
        }
      }
    });

    expect(state.currentUrl).toBe('https://example.com');
    expect(state.previewMode).toBe('vnc');
    expect(state.sandboxVncUrl).toBe('https://vnc.local');
  });

  it('loads snapshot into fresh workflow state', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'LOAD_SNAPSHOT',
      payload: {
        messages: [{ id: 'm1', role: 'user', content: 'hi', time: '10:00', logs: [], thoughtSteps: [] }],
        searchResults: [],
        toolOutputs: [{ id: 'o1', type: 'Tool', content: 'ok', time: '10:01' }],
        currentUrl: 'https://example.com',
        sandboxVncUrl: 'https://vnc.local',
        browserStatus: 'rendered',
        previewMode: 'proxy'
      }
    });

    expect(state.messages.length).toBe(1);
    expect(state.messages[0].content).toBe('hi');
    expect(state.toolOutputs.length).toBe(1);
    expect(state.currentUrl).toBe('https://example.com');
    expect(state.sessionId).toBeNull();
    expect(state.browserStatus).toBe('rendered');
  });

  it('adds execution thought steps for tool events', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'task', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_END',
        agentName: 'search_web',
        output: 'tool output'
      }
    });

    expect(state.messages[1].thoughtSteps[0]?.kind).toBe('tool_end');
    expect(state.executionTimeline[0]?.title).toContain('工具完成');
  });

  it('rolls back pending assistant placeholder', () => {
    let state = workflowReducer(initialWorkflowState, {
      type: 'SEND_USER_MESSAGE',
      payload: { content: 'task', time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'START_ASSISTANT_MESSAGE',
      payload: { time: '09:00' }
    });
    state = workflowReducer(state, {
      type: 'ROLLBACK_PENDING_ASSISTANT'
    });

    expect(state.messages).toHaveLength(1);
    expect(state.messages[0].role).toBe('user');
  });

  it('restores web snapshot from tool output when structured event is missed', () => {
    const state = workflowReducer(initialWorkflowState, {
      type: 'HANDLE_EVENT',
      payload: {
        eventType: 'TOOL_CALL_END',
        agentName: 'WebFetchTool',
        output: JSON.stringify({
          url: 'https://example.com/article',
          path: '/workspace/.openmanus/web/web-snapshot.txt',
          preview: '<html><body>Agent page</body></html>'
        })
      }
    });

    expect(state.currentUrl).toBe('https://example.com/article');
    expect(state.snapshotPath).toContain('web-snapshot.txt');
    expect(state.snapshotPreview).toContain('Agent page');
    expect(state.browserStatus).toBe('proxy-rendered');
    expect(state.previewMode).toBe('proxy');
    expect(state.webTimeline[0].title).toBe('网页快照已生成');
  });
});
