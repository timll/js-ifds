package com.ibm.wala.examples.SOAP2020.织女;

import com.ibm.wala.cast.ipa.callgraph.AstSSAPropagationCallGraphBuilder;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.intset.MutableMapping;
import magpiebridge.core.Analysis;
import magpiebridge.core.AnalysisConsumer;

import java.util.Collection;

public abstract class WalaTaintAnalysis<T> implements Analysis<AnalysisConsumer> {

	protected abstract T sourceFinder();

	protected abstract T sinkFinder();

	protected final MutableMapping<String> uriCache;

	public WalaTaintAnalysis(MutableMapping<String> uriCache) {
		this.uriCache = uriCache;
	}

	abstract public AstSSAPropagationCallGraphBuilder makeBuilder(Collection<? extends Module> files, AnalysisConsumer server) throws WalaException;

}