/*
 * Copyright (C) 2020 Grakn Labs
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

package hypergraph.concept;

import hypergraph.common.exception.HypergraphException;
import hypergraph.concept.type.AttributeType;
import hypergraph.concept.type.impl.AttributeTypeImpl;
import hypergraph.concept.type.EntityType;
import hypergraph.concept.type.impl.EntityTypeImpl;
import hypergraph.concept.type.RelationType;
import hypergraph.concept.type.impl.RelationTypeImpl;
import hypergraph.concept.type.ThingType;
import hypergraph.concept.type.impl.ThingTypeImpl;
import hypergraph.graph.Graphs;
import hypergraph.graph.util.Schema;
import hypergraph.graph.vertex.impl.TypeVertexImpl;

import static hypergraph.common.exception.Error.TypeDefinition.INVALID_VALUE_CLASS;

public class Concepts {

    private final Graphs graph;

    public Concepts(Graphs graph) {
        this.graph = graph;
    }

    public ThingType getRootType() {
        TypeVertexImpl vertex = graph.type().get(Schema.Vertex.Type.Root.THING.label());
        if (vertex != null) return new ThingTypeImpl.Root(vertex);
        else return null;
    }

    public EntityType getRootEntityType() {
        TypeVertexImpl vertex = graph.type().get(Schema.Vertex.Type.Root.ENTITY.label());
        if (vertex != null) return EntityTypeImpl.of(vertex);
        else return null;
    }

    public RelationType getRootRelationType() {
        TypeVertexImpl vertex = graph.type().get(Schema.Vertex.Type.Root.RELATION.label());
        if (vertex != null) return RelationTypeImpl.of(vertex);
        else return null;
    }

    public AttributeType getRootAttributeType() {
        TypeVertexImpl vertex = graph.type().get(Schema.Vertex.Type.Root.ATTRIBUTE.label());
        if (vertex != null) return AttributeTypeImpl.of(vertex);
        else return null;
    }

    public EntityTypeImpl putEntityType(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return EntityTypeImpl.of(vertex);
        else return EntityTypeImpl.of(graph.type(), label);
    }

    public EntityTypeImpl getEntityType(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return EntityTypeImpl.of(vertex);
        else return null;
    }

    public RelationType putRelationType(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return RelationTypeImpl.of(vertex);
        else return RelationTypeImpl.of(graph.type(), label);
    }

    public RelationType getRelationType(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return RelationTypeImpl.of(vertex);
        else return null;
    }

    public AttributeType putAttributeType(String label, Class<?> valueClass) {
        Schema.ValueClass schema = Schema.ValueClass.of(valueClass);
        if (schema == null) throw new HypergraphException(INVALID_VALUE_CLASS.format(valueClass.getCanonicalName()));
        switch (schema) {
            case BOOLEAN:
                return putAttributeTypeBoolean(label);
            case LONG:
                return putAttributeTypeLong(label);
            case DOUBLE:
                return putAttributeTypeDouble(label);
            case STRING:
                return putAttributeTypeString(label);
            case DATETIME:
                return putAttributeTypeDateTime(label);
            default:
                return null; // unreachable
        }
    }

    public AttributeType.Boolean putAttributeTypeBoolean(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return AttributeTypeImpl.Boolean.of(vertex);
        else return new AttributeTypeImpl.Boolean(graph.type(), label);
    }

    public AttributeType.Long putAttributeTypeLong(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return AttributeTypeImpl.Long.of(vertex);
        else return new AttributeTypeImpl.Long(graph.type(), label);
    }

    public AttributeType.Double putAttributeTypeDouble(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return AttributeTypeImpl.Double.of(vertex);
        else return new AttributeTypeImpl.Double(graph.type(), label);
    }

    public AttributeType.String putAttributeTypeString(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return AttributeTypeImpl.String.of(vertex);
        else return new AttributeTypeImpl.String(graph.type(), label);
    }

    public AttributeType.DateTime putAttributeTypeDateTime(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return AttributeTypeImpl.DateTime.of(vertex);
        else return new AttributeTypeImpl.DateTime(graph.type(), label);
    }

    public AttributeType getAttributeType(String label) {
        TypeVertexImpl vertex = graph.type().get(label);
        if (vertex != null) return AttributeTypeImpl.of(vertex);
        else return null;
    }
}
