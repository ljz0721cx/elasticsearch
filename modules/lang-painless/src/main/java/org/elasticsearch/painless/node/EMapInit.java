/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.painless.node;

import org.elasticsearch.painless.Location;
import org.elasticsearch.painless.Scope;
import org.elasticsearch.painless.ir.ClassNode;
import org.elasticsearch.painless.ir.MapInitializationNode;
import org.elasticsearch.painless.lookup.PainlessConstructor;
import org.elasticsearch.painless.lookup.PainlessMethod;
import org.elasticsearch.painless.lookup.def;
import org.elasticsearch.painless.symbol.ScriptRoot;

import java.util.HashMap;
import java.util.List;

import static org.elasticsearch.painless.lookup.PainlessLookupUtility.typeToCanonicalTypeName;

/**
 * Represents a map initialization shortcut.
 */
public final class EMapInit extends AExpression {
    private final List<AExpression> keys;
    private final List<AExpression> values;

    private PainlessConstructor constructor = null;
    private PainlessMethod method = null;

    public EMapInit(Location location, List<AExpression> keys, List<AExpression> values) {
        super(location);

        this.keys = keys;
        this.values = values;
    }

    @Override
    Output analyze(ScriptRoot scriptRoot, Scope scope, Input input) {
        this.input = input;
        output = new Output();

        if (input.read == false) {
            throw createError(new IllegalArgumentException("Must read from map initializer."));
        }

        output.actual = HashMap.class;

        constructor = scriptRoot.getPainlessLookup().lookupPainlessConstructor(output.actual, 0);

        if (constructor == null) {
            throw createError(new IllegalArgumentException(
                    "constructor [" + typeToCanonicalTypeName(output.actual) + ", <init>/0] not found"));
        }

        method = scriptRoot.getPainlessLookup().lookupPainlessMethod(output.actual, false, "put", 2);

        if (method == null) {
            throw createError(new IllegalArgumentException("method [" + typeToCanonicalTypeName(output.actual) + ", put/2] not found"));
        }

        if (keys.size() != values.size()) {
            throw createError(new IllegalStateException("Illegal tree structure."));
        }

        for (int index = 0; index < keys.size(); ++index) {
            AExpression expression = keys.get(index);

            Input expressionInput = new Input();
            expressionInput.expected = def.class;
            expressionInput.internal = true;
            expression.analyze(scriptRoot, scope, expressionInput);
            expression.cast();
        }

        for (int index = 0; index < values.size(); ++index) {
            AExpression expression = values.get(index);

            Input expressionInput = new Input();
            expressionInput.expected = def.class;
            expressionInput.internal = true;
            expression.analyze(scriptRoot, scope, expressionInput);
            expression.cast();
        }

        return output;
    }

    @Override
    MapInitializationNode write(ClassNode classNode) {
        MapInitializationNode mapInitializationNode = new MapInitializationNode();

        for (int index = 0; index < keys.size(); ++index) {
            mapInitializationNode.addArgumentNode(
                    keys.get(index).cast(keys.get(index).write(classNode)),
                    values.get(index).cast(values.get(index).write(classNode)));
        }

        mapInitializationNode.setLocation(location);
        mapInitializationNode.setExpressionType(output.actual);
        mapInitializationNode.setConstructor(constructor);
        mapInitializationNode.setMethod(method);

        return mapInitializationNode;
    }

    @Override
    public String toString() {
        return singleLineToString(pairwiseToString(keys, values));
    }
}
