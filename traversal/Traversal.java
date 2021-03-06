/*
 * Copyright (C) 2021 Grakn Labs
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 */

package grakn.core.traversal;

import grakn.common.collection.Pair;
import grakn.core.common.exception.GraknException;
import grakn.core.common.iterator.ResourceIterator;
import grakn.core.common.parameters.Arguments;
import grakn.core.common.parameters.Label;
import grakn.core.concurrent.producer.Producer;
import grakn.core.concurrent.producer.Producers;
import grakn.core.graph.GraphManager;
import grakn.core.graph.common.Encoding;
import grakn.core.graph.iid.VertexIID;
import grakn.core.graph.vertex.Vertex;
import grakn.core.traversal.common.Identifier;
import grakn.core.traversal.common.VertexMap;
import grakn.core.traversal.graph.TraversalVertex;
import grakn.core.traversal.planner.Planner;
import grakn.core.traversal.predicate.Predicate;
import grakn.core.traversal.predicate.PredicateArgument;
import grakn.core.traversal.structure.Structure;
import graql.lang.common.GraqlArg;
import graql.lang.common.GraqlToken;
import graql.lang.pattern.variable.Reference;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static grakn.common.collection.Collections.pair;
import static grakn.core.common.exception.ErrorMessage.Internal.ILLEGAL_STATE;
import static grakn.core.common.iterator.Iterators.cartesian;
import static grakn.core.common.iterator.Iterators.iterate;
import static grakn.core.concurrent.producer.Producers.produce;
import static grakn.core.graph.common.Encoding.Edge.ISA;
import static grakn.core.graph.common.Encoding.Edge.Thing.HAS;
import static grakn.core.graph.common.Encoding.Edge.Thing.PLAYING;
import static grakn.core.graph.common.Encoding.Edge.Thing.RELATING;
import static grakn.core.graph.common.Encoding.Edge.Type.OWNS;
import static grakn.core.graph.common.Encoding.Edge.Type.OWNS_KEY;
import static grakn.core.graph.common.Encoding.Edge.Type.PLAYS;
import static grakn.core.graph.common.Encoding.Edge.Type.RELATES;
import static grakn.core.graph.common.Encoding.Edge.Type.SUB;
import static grakn.core.graph.common.Encoding.ValueType.BOOLEAN;
import static grakn.core.graph.common.Encoding.ValueType.DATETIME;
import static grakn.core.graph.common.Encoding.ValueType.DOUBLE;
import static grakn.core.graph.common.Encoding.ValueType.LONG;
import static grakn.core.graph.common.Encoding.ValueType.STRING;
import static graql.lang.common.GraqlToken.Predicate.SubString.LIKE;
import static java.util.stream.Collectors.toList;

public class Traversal {

    private final Parameters parameters;
    private final Structure structure;
    private final Set<Identifier.Variable.Name> filter;
    private List<Planner> planners;
    private boolean modifiable;

    public Traversal() {
        structure = new Structure();
        parameters = new Parameters();
        filter = new HashSet<>();
        modifiable = true;
    }

    // TODO: We should not dynamically calculate properties like this, and then guard against 'modifiable'.
    //       We should introduce a "builder pattern" to Traversal, such that users of this library will build
    //       traversals with Traversal.Builder, and call .build() in the end to produce a final Object.
    private Set<Identifier.Variable.Name> filter() {
        if (filter.isEmpty()) {
            modifiable = false;
            iterate(structure.vertices())
                    .map(TraversalVertex::id).filter(Identifier::isName)
                    .map(id -> id.asVariable().asName()).toSet(filter);
        }
        return filter;
    }

    void initialise(TraversalCache cache) {
        planners = iterate(structure.asGraphs()).filter(p -> iterate(p.vertices()).anyMatch(
                v -> v.id().isName() && filter().contains(v.id().asVariable().asName())
        )).map(s -> cache.get(s, Planner::create)).toList();
    }

    ResourceIterator<VertexMap> iterator(GraphManager graphMgr, boolean extraPlanningTime) {
        assert !planners.isEmpty();
        if (planners.size() == 1) {
            planners.get(0).tryOptimise(graphMgr, extraPlanningTime);
            return planners.get(0).procedure().iterator(graphMgr, parameters, filter());
        } else {
            return cartesian(planners.parallelStream().map(planner -> {
                planner.tryOptimise(graphMgr, extraPlanningTime);
                return planner.procedure().iterator(graphMgr, parameters, filter());
            }).collect(toList())).map(partialAnswers -> {
                Map<Reference, Vertex<?, ?>> combinedAnswers = new HashMap<>();
                partialAnswers.forEach(p -> combinedAnswers.putAll(p.map()));
                return VertexMap.of(combinedAnswers);
            });
        }
    }

    Producer<VertexMap> producer(GraphManager graphMgr, Arguments.Query.Producer mode,
                                 int parallelisation, boolean extraPlanningTime) {
        assert !planners.isEmpty();
        if (planners.size() == 1) {
            planners.get(0).tryOptimise(graphMgr, extraPlanningTime);
            return planners.get(0).procedure().producer(graphMgr, parameters, filter(), parallelisation);
        } else {
            return Producers.producer(cartesian(planners.parallelStream().map(planner -> {
                planner.tryOptimise(graphMgr, extraPlanningTime);
                return planner.procedure().producer(graphMgr, parameters, filter(), parallelisation);
            }).map(producer -> produce(producer, mode)).collect(toList())).map(partialAnswers -> {
                Map<Reference, Vertex<?, ?>> combinedAnswers = new HashMap<>();
                partialAnswers.forEach(p -> combinedAnswers.putAll(p.map()));
                return VertexMap.of(combinedAnswers);
            }));
        }
    }

    public void equalThings(Identifier.Variable thing1, Identifier.Variable thing2) {
        assert modifiable;
        structure.equalEdge(structure.thingVertex(thing1), structure.thingVertex(thing2));
    }

    public void equalTypes(Identifier.Variable type1, Identifier.Variable type2) {
        assert modifiable;
        structure.equalEdge(structure.typeVertex(type1), structure.typeVertex(type2));
    }

    public void has(Identifier.Variable thing, Identifier.Variable attribute) {
        assert modifiable;
        structure.nativeEdge(structure.thingVertex(thing), structure.thingVertex(attribute), HAS);
    }

    public void isa(Identifier thing, Identifier.Variable type) {
        assert modifiable;
        isa(thing, type, true);
    }

    public void isa(Identifier thing, Identifier.Variable type, boolean isTransitive) {
        assert modifiable;
        structure.nativeEdge(structure.thingVertex(thing), structure.typeVertex(type), ISA, isTransitive);
    }

    public void relating(Identifier.Variable relation, Identifier.Scoped role) {
        assert modifiable;
        structure.nativeEdge(structure.thingVertex(relation), structure.thingVertex(role), RELATING);
    }

    public void playing(Identifier.Variable thing, Identifier.Scoped role) {
        assert modifiable;
        structure.nativeEdge(structure.thingVertex(thing), structure.thingVertex(role), PLAYING);
    }

    public void rolePlayer(Identifier.Variable relation, Identifier.Variable player, int repetition) {
        assert modifiable;
        structure.rolePlayer(structure.thingVertex(relation), structure.thingVertex(player), repetition);
    }

    public void rolePlayer(Identifier.Variable relation, Identifier.Variable player, Set<Label> roleTypes, int repetition) {
        assert modifiable;
        structure.rolePlayer(structure.thingVertex(relation), structure.thingVertex(player), roleTypes, repetition);
    }

    public void owns(Identifier.Variable thingType, Identifier.Variable attributeType, boolean isKey) {
        assert modifiable;
        // TODO: Something smells here. We should really just have one encoding for OWNS, and a flag for @key
        structure.nativeEdge(structure.typeVertex(thingType), structure.typeVertex(attributeType), isKey ? OWNS_KEY : OWNS);
    }

    public void plays(Identifier.Variable thingType, Identifier.Variable roleType) {
        assert modifiable;
        structure.nativeEdge(structure.typeVertex(thingType), structure.typeVertex(roleType), PLAYS);
    }

    public void relates(Identifier.Variable relationType, Identifier.Variable roleType) {
        assert modifiable;
        structure.nativeEdge(structure.typeVertex(relationType), structure.typeVertex(roleType), RELATES);
    }

    public void sub(Identifier.Variable subtype, Identifier.Variable supertype, boolean isTransitive) {
        assert modifiable;
        structure.nativeEdge(structure.typeVertex(subtype), structure.typeVertex(supertype), SUB, isTransitive);
    }

    public void iid(Identifier.Variable thing, byte[] iid) {
        assert modifiable;
        parameters.putIID(thing, VertexIID.Thing.of(iid));
        structure.thingVertex(thing).props().hasIID(true);
    }

    public void types(Identifier thing, Set<Label> labels) {
        assert modifiable;
        structure.thingVertex(thing).props().types(labels);
    }

    public void isAbstract(Identifier.Variable type) {
        assert modifiable;
        structure.typeVertex(type).props().setAbstract();
    }

    public void labels(Identifier.Variable type, Label label) {
        assert modifiable;
        structure.typeVertex(type).props().labels(label);
    }

    public void labels(Identifier.Variable type, Set<Label> label) {
        assert modifiable;
        structure.typeVertex(type).props().labels(label);
    }

    public void regex(Identifier.Variable type, String regex) {
        assert modifiable;
        structure.typeVertex(type).props().regex(regex);
    }

    public void valueType(Identifier.Variable attributeType, GraqlArg.ValueType valueType) {
        assert modifiable;
        structure.typeVertex(attributeType).props().valueType(Encoding.ValueType.of(valueType));
    }

    public void predicate(Identifier.Variable attribute, GraqlToken.Predicate token, String value) {
        assert modifiable;
        Predicate.Value.String predicate = Predicate.Value.String.of(token);
        structure.thingVertex(attribute).props().predicate(predicate);
        if (token == LIKE) parameters.pushValue(attribute, predicate, new Parameters.Value(Pattern.compile(value)));
        else parameters.pushValue(attribute, predicate, new Parameters.Value(value));
    }

    public void predicate(Identifier.Variable attribute, GraqlToken.Predicate.Equality token, Boolean value) {
        assert modifiable;
        Predicate.Value.Numerical predicate = Predicate.Value.Numerical.of(token, PredicateArgument.Value.BOOLEAN);
        parameters.pushValue(attribute, predicate, new Parameters.Value(value));
        structure.thingVertex(attribute).props().predicate(predicate);
    }

    public void predicate(Identifier.Variable attribute, GraqlToken.Predicate.Equality token, Long value) {
        assert modifiable;
        Predicate.Value.Numerical predicate = Predicate.Value.Numerical.of(token, PredicateArgument.Value.LONG);
        parameters.pushValue(attribute, predicate, new Parameters.Value(value));
        structure.thingVertex(attribute).props().predicate(predicate);
    }

    public void predicate(Identifier.Variable attribute, GraqlToken.Predicate.Equality token, Double value) {
        assert modifiable;
        long longValue = Math.round(value);
        if (Predicate.compareDoubles(value, longValue) == 0) {
            predicate(attribute, token, longValue);
        } else {
            Predicate.Value.Numerical predicate = Predicate.Value.Numerical.of(token, PredicateArgument.Value.DOUBLE);
            parameters.pushValue(attribute, predicate, new Parameters.Value(value));
            structure.thingVertex(attribute).props().predicate(predicate);
        }
    }

    public void predicate(Identifier.Variable attribute, GraqlToken.Predicate.Equality token, LocalDateTime value) {
        assert modifiable;
        Predicate.Value.Numerical predicate = Predicate.Value.Numerical.of(token, PredicateArgument.Value.DATETIME);
        parameters.pushValue(attribute, predicate, new Parameters.Value(value));
        structure.thingVertex(attribute).props().predicate(predicate);
    }

    public void predicate(Identifier.Variable att1, GraqlToken.Predicate.Equality token, Identifier.Variable att2) {
        assert modifiable;
        Predicate.Variable predicate = Predicate.Variable.of(token);
        structure.predicateEdge(structure.thingVertex(att1), structure.thingVertex(att2), predicate);
    }

    public void filter(Set<Identifier.Variable.Name> filter) {
        assert modifiable;
        this.filter.addAll(filter);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Traversal that = (Traversal) o;

        // We compare this.filter() instead of this.filter, as the property is dynamically calculated
        return (this.structure.equals(that.structure) &&
                this.parameters.equals(that.parameters) &&
                this.filter().equals(that.filter()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.structure, this.parameters, this.filter);
    }

    public static class Parameters {

        private final Map<Identifier.Variable, VertexIID.Thing> iid;
        private final Map<Pair<Identifier.Variable, Predicate.Value<?>>, Set<Value>> values;

        public Parameters() {
            iid = new HashMap<>();
            values = new HashMap<>();
        }

        public void putIID(Identifier.Variable identifier, VertexIID.Thing iid) {
            assert !this.iid.containsKey(identifier);
            this.iid.put(identifier, iid);
        }

        public void pushValue(Identifier.Variable identifier, Predicate.Value<?> predicate, Value value) {
            values.computeIfAbsent(pair(identifier, predicate), k -> new HashSet<>()).add(value);
        }

        public VertexIID.Thing getIID(Identifier.Variable identifier) {
            return iid.get(identifier);
        }

        public Set<Value> getValues(Identifier.Variable identifier, Predicate.Value<?> predicate) {
            return values.get(pair(identifier, predicate));
        }

        @Override
        public String toString() {
            StringBuilder str = new StringBuilder().append("Parameters: {");
            if (!iid.isEmpty()) str.append("\n\tiid: ").append(iid);
            if (!values.isEmpty()) str.append("\n\tvalues: ").append(values);
            str.append("\n}");
            return str.toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Parameters that = (Parameters) o;

            return iid.equals(that.iid) && values.equals(that.values);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iid, values);
        }

        public static class Value {

            private final Encoding.ValueType valueType;
            private final Boolean booleanVal;
            private final Long longVal;
            private final Double doubleVal;
            private final String stringVal;
            private final LocalDateTime dateTimeVal;
            private final Pattern regexPattern;
            private final int hash;

            Value(boolean value) {
                this(BOOLEAN, value, null, null, null, null, null);
            }

            Value(long value) {
                this(LONG, null, value, null, null, null, null);
            }

            Value(double value) {
                this(DOUBLE, null, null, value, null, null, null);
            }

            Value(LocalDateTime value) {
                this(DATETIME, null, null, null, value, null, null);
            }

            Value(String value) {
                this(STRING, null, null, null, null, value, null);
            }

            Value(Pattern regex) {
                this(STRING, null, null, null, null, null, regex);
            }

            private Value(Encoding.ValueType valueType, Boolean booleanVal, Long longVal, Double doubleVal,
                          LocalDateTime dateTimeVal, String stringVal, Pattern regexPattern) {
                this.valueType = valueType;
                this.booleanVal = booleanVal;
                this.longVal = longVal;
                this.doubleVal = doubleVal;
                this.dateTimeVal = dateTimeVal;
                this.stringVal = stringVal;
                this.regexPattern = regexPattern;
                this.hash = Objects.hash(valueType, booleanVal, longVal, doubleVal, dateTimeVal, stringVal, regexPattern);
            }

            public Encoding.ValueType valueType() {
                return valueType;
            }

            public boolean isBoolean() { return booleanVal != null; }

            public boolean isLong() { return longVal != null; }

            public boolean isDouble() { return doubleVal != null; }

            public boolean isDateTime() { return dateTimeVal != null; }

            public boolean isString() { return stringVal != null; }

            public boolean isRegex() { return regexPattern != null; }

            public Boolean getBoolean() { return booleanVal; }

            public Long getLong() { return longVal; }

            public Double getDouble() {
                if (isDouble()) return doubleVal;
                else if (isLong()) return longVal.doubleValue();
                else return null;
            }

            public LocalDateTime getDateTime() { return dateTimeVal; }

            public String getString() { return stringVal; }

            public Pattern getRegex() { return regexPattern; }

            @Override
            public String toString() {
                if (isBoolean()) return "boolean: " + booleanVal;
                else if (isLong()) return "long: " + longVal;
                else if (isDouble()) return "double: " + doubleVal;
                else if (isDateTime()) return "datetime: " + dateTimeVal;
                else if (isString()) return "string: " + stringVal;
                else if (isRegex()) return "regex: " + regexPattern.pattern();
                else throw GraknException.of(ILLEGAL_STATE);
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;
                if (o == null || getClass() != o.getClass()) return false;

                Value that = (Value) o;
                return (Objects.equals(this.valueType, that.valueType) &&
                        Objects.equals(this.booleanVal, that.booleanVal) &&
                        Objects.equals(this.longVal, that.longVal) &&
                        Objects.equals(this.doubleVal, that.doubleVal) &&
                        Objects.equals(this.dateTimeVal, that.dateTimeVal) &&
                        Objects.equals(this.stringVal, that.stringVal) &&
                        Objects.equals(this.regexPattern, that.regexPattern));
            }

            @Override
            public int hashCode() {
                return hash;
            }
        }
    }
}
