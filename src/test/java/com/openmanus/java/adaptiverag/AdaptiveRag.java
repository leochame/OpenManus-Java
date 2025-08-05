package com.openmanus.java.adaptiverag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.state.AgentState;

import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.LogManager;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;
import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

/**
 * 自适应RAG（检索增强生成）系统的核心实现类
 *
 * 该类实现了一个智能的问答系统，能够根据问题的性质自动选择最合适的信息检索策略：
 * - 对于特定领域的问题，使用向量数据库进行检索
 * - 对于通用问题，使用网络搜索获取最新信息
 * - 通过多层次的质量评估确保答案的准确性和相关性
 *
 * 工作流程包括：
 * 1. 问题路由：判断使用向量检索还是网络搜索
 * 2. 文档检索：获取相关文档或网络内容
 * 3. 文档评分：评估检索内容的相关性
 * 4. 答案生成：基于相关文档生成回答
 * 5. 质量检查：检测幻觉并验证答案质量
 * 6. 查询优化：必要时重写问题并重新检索
 */
public class AdaptiveRag {

    // 日志记录器，用于跟踪系统运行状态和调试信息
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger("AdaptiveRag");

    /**
     * 图状态类，表示整个RAG工作流程中的状态信息
     *
     * 该类继承自AgentState，用于在不同的处理节点之间传递和维护状态信息。
     * 包含以下核心属性：
     * - question: 用户的原始问题或经过重写的问题
     * - generation: 大语言模型生成的答案
     * - documents: 检索到的相关文档列表
     */
    public static class State extends AgentState {

        /**
         * 构造函数，初始化状态对象
         * @param initData 初始化数据映射，包含状态的初始值
         */
        public State(Map<String, Object> initData) {
            super(initData);
        }

        /**
         * 获取当前问题
         * @return 当前的问题字符串
         * @throws IllegalStateException 如果问题未设置
         */
        public String question() {
            Optional<String> result = value("question");
            return result.orElseThrow( () -> new IllegalStateException( "question is not set!" ) );
        }

        /**
         * 获取生成的答案
         * @return 包含生成答案的Optional对象，如果未生成则为空
         */
        public Optional<String> generation() {
            return value("generation");
        }

        /**
         * 获取检索到的文档列表
         * @return 文档内容的字符串列表，如果没有文档则返回空列表
         */
        public List<String> documents() {
            return this.<List<String>>value("documents").orElse(emptyList());
        }

    }

    // OpenAI API密钥，用于访问GPT模型进行文本生成和评分
    private final String openApiKey;
    // Tavily API密钥，用于网络搜索功能
    private final String tavilyApiKey;
    // ChromaDB向量存储实例，用于文档检索（延迟初始化）
    private ChromaStore chroma;

    /**
     * 获取ChromaDB向量存储实例
     * 使用延迟初始化模式，只在首次访问时创建实例，提高性能
     * @return ChromaStore实例
     */
    public ChromaStore getChroma() {
        if( chroma == null ) {
            chroma = openChroma();
        }
        return chroma;
    }

    /**
     * AdaptiveRag类的构造函数
     *
     * @param openApiKey  OpenAI API密钥，用于访问GPT模型服务
     * @param tavilyApiKey  Tavily API密钥，用于网络搜索功能
     *
     * 该构造函数通过验证提供的API密钥来初始化AdaptiveRag实例。
     * 两个密钥都是必需的，用于系统的不同功能模块。如果任一密钥为null，
     * 将抛出异常，确保AdaptiveRag实例始终正确配置。
     */
    public AdaptiveRag(String openApiKey, String tavilyApiKey) {
        this.openApiKey     = Objects.requireNonNull(openApiKey, "no OPENAI APIKEY provided!");
        this.tavilyApiKey   = Objects.requireNonNull(tavilyApiKey, "no TAVILY APIKEY provided!");
        // ChromaStore实例采用延迟初始化，通过getChroma()方法访问时才创建
    }

    /**
     * 创建并返回ChromaDB向量存储实例
     * @return 新的ChromaStore实例
     */
    private ChromaStore openChroma() {
        return ChromaStore.of(openApiKey);
    }

    /**
     * 文档检索节点：从向量数据库中检索与问题相关的文档
     *
     * 该方法使用语义搜索在ChromaDB中查找与用户问题最相关的文档。
     * 通过将问题转换为向量嵌入，然后在向量空间中寻找最相似的文档片段。
     *
     * @param state 当前图状态，包含用户问题
     * @return 包含检索到的文档列表和原问题的状态更新映射
     */
    private Map<String,Object> retrieve( State state ) {
        log.debug("---RETRIEVE---");

        // 获取用户问题
        String question = state.question();

        // 在向量数据库中搜索相关文档
        EmbeddingSearchResult<TextSegment> relevant = this.getChroma().search( question );

        // 提取匹配文档的文本内容
        List<String> documents = relevant.matches().stream()
                .map( m -> m.embedded().text() )
                .toList();

        // 返回检索到的文档和原问题
        return Map.of( "documents", documents , "question", question );
    }

    /**
     * 答案生成节点：基于检索到的文档生成回答
     *
     * 该方法使用大语言模型（GPT）根据用户问题和相关文档生成准确的答案。
     * 生成过程会考虑文档的上下文信息，确保答案的相关性和准确性。
     *
     * @param state 当前图状态，包含问题和相关文档
     * @return 包含生成答案的状态更新映射
     */
    private Map<String,Object> generate( State state ) {
        log.debug("---GENERATE---");

        // 获取问题和相关文档
        String question = state.question();
        List<String> documents = state.documents();

        // 使用Generation服务生成答案
        String generation = (new Generation(openApiKey)).apply(question, documents);

        // 返回生成的答案
        return Map.of("generation", generation);
    }

    /**
     * 文档评分节点：评估检索到的文档与问题的相关性
     *
     * 该方法使用AI评分器对每个检索到的文档进行相关性评估，过滤掉不相关的文档。
     * 这是质量控制的重要环节，确保只有真正相关的文档被用于答案生成。
     * 评分标准包括关键词匹配和语义相关性。
     *
     * @param state 当前图状态，包含问题和待评估的文档列表
     * @return 包含过滤后相关文档列表的状态更新映射
     */
    private Map<String,Object> gradeDocuments( State state ) {
        log.debug("---CHECK DOCUMENT RELEVANCE TO QUESTION---");

        // 获取问题和文档列表
        String question = state.question();
        List<String> documents = state.documents();

        // 创建文档相关性评分器
        final RetrievalGrader grader = new RetrievalGrader( openApiKey );

        // 过滤相关文档：对每个文档进行相关性评分
        List<String> filteredDocs =  documents.stream()
                .filter( d -> {
                    // 使用AI评分器评估文档相关性
                    RetrievalGrader.Score score = grader.apply( new RetrievalGrader.Arguments(question, d ));
                    boolean relevant = score.binaryScore.equals("yes");

                    // 记录评分结果
                    if( relevant ) {
                        log.debug("---GRADE: DOCUMENT RELEVANT---");
                    }
                    else {
                        log.debug("---GRADE: DOCUMENT NOT RELEVANT---");
                    }
                    return relevant;
                })
                .toList();

        // 返回过滤后的相关文档列表
        return Map.of( "documents", filteredDocs);
    }

    /**
     * 查询转换节点：重写问题以提高检索效果
     *
     * 当检索到的文档质量不佳时，该方法会重新表述用户的问题，
     * 使其更适合向量检索。通过改进问题的表达方式和关键词选择，
     * 提高后续检索的准确性和相关性。
     *
     * @param state 当前图状态，包含原始问题
     * @return 包含重写后问题的状态更新映射
     */
    private Map<String,Object> transformQuery( State state ) {
        log.debug("---TRANSFORM QUERY---");

        // 获取当前问题
        String question = state.question();

        // 使用问题重写器生成更好的问题表述
        String betterQuestion = (new QuestionRewriter( openApiKey )).apply( question );

        // 返回重写后的问题
        return Map.of( "question", betterQuestion );
    }

    /**
     * 网络搜索节点：基于问题进行网络搜索
     *
     * 当问题超出向量数据库的知识范围时，该方法会使用网络搜索
     * 获取最新的相关信息。搜索结果会被整合为文档格式，
     * 供后续的答案生成使用。
     *
     * @param state 当前图状态，包含搜索问题
     * @return 包含网络搜索结果的状态更新映射
     */
    private Map<String,Object> webSearch( State state ) {
        log.debug("---WEB SEARCH---");

        // 获取搜索问题
        String question = state.question();

        // 使用网络搜索工具获取相关内容
        List<dev.langchain4j.rag.content.Content> result = (new WebSearchTool( tavilyApiKey )).apply(question);

        // 将搜索结果合并为单个文档字符串
        String webResult = result.stream()
                            .map( content -> content.textSegment().text() )
                            .collect(Collectors.joining("\n"));

        // 返回网络搜索结果作为文档
        return Map.of( "documents", List.of( webResult ) );
    }

    /**
     * 问题路由边：决定使用网络搜索还是向量检索
     *
     * 该方法分析用户问题的性质，智能决定最适合的信息检索策略：
     * - 对于特定领域（如AI代理、提示工程等）的问题，使用向量数据库
     * - 对于通用或时效性问题，使用网络搜索获取最新信息
     *
     * @param state 当前图状态，包含用户问题
     * @return 下一个要调用的节点名称（"web_search" 或 "vectorstore"）
     */
    private String routeQuestion( State state  ) {
        log.debug("---ROUTE QUESTION---");

        // 获取用户问题
        String question = state.question();

        // 使用问题路由器判断最佳检索策略
        QuestionRouter.Type source = (new QuestionRouter( openApiKey )).apply( question );

        // 记录路由决策
        if( source == QuestionRouter.Type.web_search ) {
            log.debug("---ROUTE QUESTION TO WEB SEARCH---");
        }
        else {
            log.debug("---ROUTE QUESTION TO RAG---");
        }

        // 返回路由目标
        return source.name();
    }

    /**
     * 生成决策边：决定是生成答案还是重新处理问题
     *
     * 该方法评估文档评分的结果，决定下一步行动：
     * - 如果有相关文档，则进行答案生成
     * - 如果没有相关文档，则转换查询并重新检索
     *
     * @param state 当前图状态，包含评分后的文档列表
     * @return 下一个要调用的节点名称（"generate" 或 "transform_query"）
     */
    private String decideToGenerate( State state  ) {
        log.debug("---ASSESS GRADED DOCUMENTS---");

        // 获取评分后的文档列表
        List<String> documents = state.documents();

        // 如果没有相关文档，需要重新转换查询
        if(documents.isEmpty()) {
            log.debug("---DECISION: ALL DOCUMENTS ARE NOT RELEVANT TO QUESTION, TRANSFORM QUERY---");
            return "transform_query";
        }

        // 有相关文档，可以进行答案生成
        log.debug( "---DECISION: GENERATE---" );
        return "generate";
    }

    /**
     * Edge: Determines whether the generation is grounded in the document and answers question.
     * @param state The current graph state
     * @return Decision for next node to call
     */
    private String gradeGeneration_v_documentsAndQuestion( State state ) {
        log.debug("---CHECK HALLUCINATIONS---");

        String question = state.question();
        List<String> documents = state.documents();
        String generation = state.generation()
                .orElseThrow( () -> new IllegalStateException( "generation is not set!" ) );


        HallucinationGrader.Score score = (new HallucinationGrader( openApiKey ))
                                            .apply( new HallucinationGrader.Arguments(documents, generation));

        if(Objects.equals(score.binaryScore, "yes")) {
            log.debug( "---DECISION: GENERATION IS GROUNDED IN DOCUMENTS---" );
            log.debug("---GRADE GENERATION vs QUESTION---");
            AnswerGrader.Score score2 = (new AnswerGrader( openApiKey ))
                                            .apply( new AnswerGrader.Arguments(question, generation) );
            if( Objects.equals( score2.binaryScore, "yes") ) {
                log.debug( "---DECISION: GENERATION ADDRESSES QUESTION---" );
                return "useful";
            }

            log.debug("---DECISION: GENERATION DOES NOT ADDRESS QUESTION---");
            return "not useful";
        }

        log.debug( "---DECISION: GENERATION IS NOT GROUNDED IN DOCUMENTS, RE-TRY---" );
        return "not supported";
    }

    public StateGraph<State> buildGraph() throws Exception {
        return new StateGraph<>(State::new)
            // Define the nodes
            .addNode("web_search", node_async(this::webSearch) )  // web search
            .addNode("retrieve", node_async(this::retrieve) )  // retrieve
            .addNode("grade_documents",  node_async(this::gradeDocuments) )  // grade documents
            .addNode("generate", node_async(this::generate) )  // generatae
            .addNode("transform_query", node_async(this::transformQuery))  // transform_query
            // Build graph
            .addConditionalEdges(START,
                    edge_async(this::routeQuestion),
                    Map.of(
                        "web_search", "web_search",
                        "vectorstore", "retrieve"
                    ))

            .addEdge("web_search", "generate")
            .addEdge("retrieve", "grade_documents")
            .addConditionalEdges(
                    "grade_documents",
                    edge_async(this::decideToGenerate),
                    Map.of(
                        "transform_query","transform_query",
                        "generate", "generate"
                    ))
            .addEdge("transform_query", "retrieve")
            .addConditionalEdges(
                    "generate",
                    edge_async(this::gradeGeneration_v_documentsAndQuestion),
                    Map.of(
                            "not supported", "generate",
                            "useful", END,
                            "not useful", "transform_query"
                    ))
             ;
    }

    public static void main( String[] args ) throws Exception {
        try(FileInputStream configFile = new FileInputStream("logging.properties")) {
            LogManager.getLogManager().readConfiguration(configFile);
        };

        AdaptiveRag adaptiveRagTest = new AdaptiveRag( System.getenv("OPENAI_API_KEY"), System.getenv("TAVILY_API_KEY"));

        CompiledGraph<State> graph = adaptiveRagTest.buildGraph().compile();

        org.bsc.async.AsyncGenerator<org.bsc.langgraph4j.NodeOutput<State>> result = graph.stream( Map.of( "question", "What player at the Bears expected to draft first in the 2024 NFL draft?" ) );
        // var result = graph.stream( Map.of( "question", "What kind the agent memory do iu know?" ) );

        String generation = "";
        for( org.bsc.langgraph4j.NodeOutput<State> r : result ) {
            System.out.printf( "Node: '%s':\n", r.node() );

            generation = r.state().generation().orElse( "")
            ;
        }

        System.out.println( generation );

        // generate plantuml script
        // var plantUml = graph.getGraph( GraphRepresentation.Type.PLANTUML );
        // System.out.println( plantUml );

    }

}

