package org.opendroneid.rpi;

import java.util.function.Consumer;

public class ObservableValue<T> {
    private T value;
    private Consumer<T> observer;

    public T getValue() { return value; }

    public void setValue(T value) {
        this.value = value;
        if (observer != null) observer.accept(value);
    }

    public void observe(Consumer<T> observer) {
        this.observer = observer;
    }
}
