package org.mammoth.compiler;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

public class MammothErrorListener extends BaseErrorListener {
    private final String sourceName;

    public MammothErrorListener(String sourceName) {
        this.sourceName = sourceName;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg, RecognitionException e) {
        String message = String.format("%s:%d:%d: %s", sourceName, line, charPositionInLine + 1, msg);
        throw new RuntimeException(message);
    }
}
