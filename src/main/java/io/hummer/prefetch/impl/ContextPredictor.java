package io.hummer.prefetch.impl;

/**
 * "Predicts" and generates future instances of the context information.
 * @author Waldemar Hummer
 */
public interface ContextPredictor<T> {

	Context<T> predict(Context<T> currentContext, double time);

	//Context<T> predict(Context<T> currentContext, double time);

}
