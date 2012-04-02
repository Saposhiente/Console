/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.github.Saposhiente.Console;

/**
 *
 * @author grif
 */
public class SyntaxError extends Throwable {

    SyntaxError(String string) {
        super(string);
    }

    SyntaxError() {
        super();
    }
    SyntaxError(Throwable t) {
        super(t);
    }
    SyntaxError(String s, Throwable t) {
        super(s, t);
    }
}
