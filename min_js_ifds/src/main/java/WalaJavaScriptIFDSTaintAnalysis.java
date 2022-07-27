import com.ibm.wala.cast.js.ipa.callgraph.JSCFABuilder;
import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil;
import com.ibm.wala.cast.js.translator.CAstRhinoTranslatorFactory;
import com.ibm.wala.cast.js.util.JSCallGraphBuilderUtil;
import com.ibm.wala.cast.loader.AstMethod;
import com.ibm.wala.cast.tree.CAstSourcePositionMap;
import com.ibm.wala.cast.util.SourceBuffer;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.IFDS.*;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ssa.*;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.HashSetFactory;
import com.ibm.wala.util.collections.Pair;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import javax.swing.plaf.synth.SynthTextAreaUI;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
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
    }

    public static void main(String... args) throws IllegalArgumentException, CancelException, WalaException, IOException {
//        String path = args[0];
        String path = "file:///home/tim/Projects/wala-ifds/test.js";
        JSCallGraphUtil.setTranslatorFactory(new CAstRhinoTranslatorFactory());
        URL url = new URL(path);
        JSCFABuilder B = JSCallGraphBuilderUtil.makeScriptCGBuilder("/home/tim/Projects/wala-ifds/", "test.js");
//        JSCFABuilder B = JSCallGraphBuilderUtil.makeHTMLCGBuilder(url);
        CallGraph CG = B.makeCallGraph(B.getOptions());

        Function<BasicBlockInContext<IExplodedBasicBlock>, Boolean> sources = (ebb) -> {
            SSAInstruction inst = ebb.getDelegate().getInstruction();

//            System.out.println(ebb.getMethod().getDeclaringClass().getName().toString());
            /* If this is an entry of a method that was called using invokeFunction, taint their parameters. */
            if (ebb.getMethod().getDeclaringClass().getName().toString().equals("Ltest.js/calledFromJava") && ebb.isEntryBlock()) {
                for (IField f : ebb.getMethod().getDeclaringClass().getAllFields()) {
                    System.out.println(f.getName().toString());
                }
            }

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
                System.out.println("sink " + sbb.getDelegate().getInstruction());
                BasicBlockInContext<IExplodedBasicBlock> bb= sbb;
                List<CAstSourcePositionMap.Position> witness = new LinkedList<>();
                steps: while (bb != null) {
                    IntSet r = R.getResult(bb);
                    SSAInstruction inst = bb.getDelegate().getInstruction();
                    if (bb.getMethod() instanceof AstMethod) {
                        CAstSourcePositionMap.Position pos = ((AstMethod)bb.getMethod()).debugInfo().getInstructionPosition(inst.iIndex());
                        witness.add(0, pos);
                    }
                    IntIterator vals = r.intIterator();
                    while(vals.hasNext()) {
                        int i = vals.next();
                        Pair<Integer, BasicBlockInContext<IExplodedBasicBlock>> vn = A.domain.getMappedObject(i);
                        for(int j = 0; j < inst.getNumberOfUses(); j++) {
                            if (inst.getUse(j) == vn.fst) {
                                bb = vn.snd;
                                System.out.println("step " + bb.getDelegate().getInstruction());
                                continue steps;
                            }
                        }
                    }
                    bb = null;
                }
                if (witness.size() > 0) {
                    result.add(witness);
                }
            }
        });
        return result;
    }
}
