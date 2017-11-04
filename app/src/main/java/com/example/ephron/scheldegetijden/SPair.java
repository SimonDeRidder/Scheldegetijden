package com.example.ephron.scheldegetijden;

import java.io.Serializable;
import java.util.Objects;

class SPair<T,V> implements Serializable
{
    final T first;
    final V second;


    SPair(T first, V second)
    {
        this.first = first;
        this.second = second;
    }

    @Override
    public boolean equals(Object o)
    {
        if (o instanceof SPair)
        {
            SPair<?, ?> p = (SPair<?, ?>) o;
            return Objects.equals(p.first, first) && Objects.equals(p.second, second);
        }
        return false;
    }

    @Override
    public int hashCode()
    {
        return (first == null ? 0 : first.hashCode()) ^ (second == null ? 0 : second.hashCode());
    }

    @Override
    public String toString()
    {
        return "Pair{" + String.valueOf(first) + " " + String.valueOf(second) + "}";
    }

// --Commented out by Inspection START (4/11/2017 12:05):
//    public static <A, B> SPair<A, B> create(A a, B b)
//    {
//        return new SPair<>(a, b);
//    }
// --Commented out by Inspection STOP (4/11/2017 12:05)
}
