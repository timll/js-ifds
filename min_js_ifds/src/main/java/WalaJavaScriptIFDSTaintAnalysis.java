import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.dataflow.IFDS.*;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.*;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Function;

public class WalaJavaScriptIFDSTaintAnalysis {

    private final ICFGSupergraph supergraph;

    private final JSDomain domain;

    private final Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources;

    public WalaJavaScriptIFDSTaintAnalysis(CallGraph cg, Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources)
    {
        supergraph = ICFGSupergraph.make(cg);
        this.sources = sources;
        this.domain = new JSDomain();

//        supergraph.getProcedureGraph().forEach(cgNode -> {
//            System.out.println(cgNode.toString());
//        });

    }

    public static void main(String... args) throws IllegalArgumentException, CancelException, WalaException, IOException {
//        String path = args[0];
        String path = "file:///home/tim/Projects/wala-ifds/test.js";
        JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());
        URL url = new URL(path);
        JSCFABuilder B = JSCallGraphBuilderUtil.makeScriptCGBuilder("/home/tim/Projects/wala-ifds/", "test.js");
//        JSCFABuilder B = JSCallGraphBuilderUtil.makeHTMLCGBuilder(url);
        AnalysisOptions opt = B.getOptions();
//        JavaScriptEntryPoints eps = JSCallGraphBuilderUtil.makeScriptRoots(B.getClassHierarchy());
        Iterable<? extends Entrypoint> jsEps = opt.getEntrypoints();
//        jsEps.forEach(entrypoint -> entrypoint.);
        CallGraph CG = B.makeCallGraph(opt);
        CG.forEach(cgNode -> System.out.println(cgNode.getIR().toString()));

        Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources = (ebb) -> {
//            System.out.println(ebb.getMethod().getDeclaringClass().toString());
            SSAInstruction inst = ebb.getDelegate().getInstruction();


            if (inst instanceof SSAAbstractInvokeInstruction) {
                for (CGNode target : CG.getPossibleTargets(ebb.getNode(), ((SSAAbstractInvokeInstruction) inst).getCallSite())) {
                    if (target.getMethod().getDeclaringClass().getName().toString().endsWith("source"))
                        return true;
                }
            }

            return false;
        };

        Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sinks = (bb) -> {
            SSAInstruction inst = bb.getDelegate().getInstruction();
          if (inst instanceof SSAAbstractInvokeInstruction) {
                for (CGNode target : CG.getPossibleTargets(bb.getNode(), ((SSAAbstractInvokeInstruction) inst).getCallSite())) {
                    if (target.getMethod().getDeclaringClass().getName().toString().endsWith("sink"))
                        return true;
                }
            }
            return false;
        };

        analyzeTaint(CG, sources, sinks).forEach(witness -> witness.forEach(step -> {
            try {
                System.out.println(new SourceBuffer(step));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }));
    }

    public TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer,BasicBlockInContext<IExplodedBasicBlock>>> analyze() {
        PartiallyBalancedTabulationSolver<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> solver = PartiallyBalancedTabulationSolver
                .createPartiallyBalancedTabulationSolver(new JSProblem(domain, supergraph, sources), null);
        TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> result = null;
        try {
            result = solver.solve();
        } catch (CancelException e) {
            // this shouldn't happen
            assert false;
        }
        return result;
    }
    public static Set<List<CAstSourcePositionMap.Position>> analyzeTaint(CallGraph CG, Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources, Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sinks) {
        WalaJavaScriptIFDSTaintAnalysis A = new WalaJavaScriptIFDSTaintAnalysis(CG, sources);

        TabulationResult<BasicBlockInContext<IExplodedBasicBlock>, CGNode, Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>>> R = A.analyze();

        Set<List<CAstSourcePositionMap.Position>> result = HashSetFactory.make();

        R.getSupergraphNodesReached().forEach((sbb) -> {
            if (sinks.apply(sbb)) {
                IntSet r = R.getResult(sbb);
                System.out.println("sink " + sbb.getDelegate().getInstruction());
                BasicBlockInContext<IExplodedBasicBlock> bb = sbb;
                List<CAstSourcePositionMap.Position> witness = new LinkedList<>();
                SSAInstruction inst = bb.getDelegate().getInstruction();

                IntIterator vals = r.intIterator();
                MutableIntSet taintsReachingStmt = IntSetUtil.make();
                /* Find names of all taints reaching the end. */
                while (vals.hasNext()) {
                    int i = vals.next();
                    /* Map index to SSA int. */
                    Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn = A.domain.getMappedObject(i);
                    taintsReachingStmt.add(vn.fst);
                    System.out.println("Tainted vars at statement: " + Arrays.toString(bb.getNode().getIR().getLocalNames(1, vn.fst)));
                }


                if (inst instanceof SSAAbstractInvokeInstruction) {
                    SSAAbstractInvokeInstruction invokeInst = (SSAAbstractInvokeInstruction) inst;
                    MutableIntSet paramIdx = IntSetUtil.make();
                    /* Get all param indices. */
                    int n = ((SSAAbstractInvokeInstruction) inst).getNumberOfPositionalParameters();
                    for (int i = 1; i < n; i++) {
                        paramIdx.add(inst.getUse(i));
                    }
                    IntSet taintedParams = paramIdx.intersection(taintsReachingStmt);
                    /* If paramIt is non-zero, a parameter is tainted. */
                    IntIterator paramIt = taintedParams.intIterator();
                    while (paramIt.hasNext())
                    {
                        int next = paramIt.next();
                        System.out.println("Tainted Params at sink: " + Arrays.toString(bb.getNode().getIR().getLocalNames(1, next)));
                    }
                }
            }
        });
        return result;
    }
}
