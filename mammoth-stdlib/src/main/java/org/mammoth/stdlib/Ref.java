package org.mammoth.stdlib;

/**
 * Reference container for reference-captured variables in closures.
 * Used to implement PHP-style &amp;$var capture semantics on the JVM.
 *
 * <p>When a closure uses {@code use (&amp;$var)}, the compiler wraps
 * {@code $var} in a {@code Ref} object. All reads and writes to the
 * variable inside and outside the closure go through {@code ref.value}.</p>
 *
 * @param <T> the type of the referenced value
 */
public class Ref<T> {
    public T value;

    public Ref() {}

    public Ref(T value) {
        this.value = value;
    }
}
