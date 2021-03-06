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

package grakn.core.common.exception;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * TODO there is some technical debt in the way we throw and catch GraknException, which is a RuntimeException.
 * see issue #6021
 */
public class GraknException extends RuntimeException {

    @Nullable
    private final ErrorMessage errorMessage;

    private GraknException(String error) {
        super(error);
        errorMessage = null;
    }

    private GraknException(ErrorMessage error, Object... parameters) {
        super(error.message(parameters));
        assert !getMessage().contains("%s");
        this.errorMessage = error;
    }

    private GraknException(Throwable e) {
        super(e);
        errorMessage = null;
    }

    public static GraknException of(Throwable e) {
        return new GraknException(e);
    }

    public static GraknException of(ErrorMessage errorMessage, Object... parameters) {
        return new GraknException(errorMessage, parameters);
    }

    public Optional<String> code() {
        return Optional.ofNullable(errorMessage).map(grakn.common.exception.ErrorMessage::code);
    }

    public static GraknException of(List<GraknException> exceptions) {
        StringBuilder messages = new StringBuilder();
        for (GraknException exception : exceptions) {
            messages.append(exception.getMessage()).append("\n");
        }
        return new GraknException(messages.toString());
    }
}
