"""PushDown Automata based arithmetic calculator."""
from operator import add, sub, mul, div
from functools import partial

# Mapping of operator tokens to operator functions
ops = {'+': add, '-': sub, '*': mul, '/': div}

# Executable error message templates
tail = ' in "{}"'
adj_ops_error = partial(("Error: adjacent ops '{}' & '{}'" + tail).format)
no_left_error = partial(("Error: no left operand for '{}'" + tail).format)
no_op_error = partial(("Error: no operator between {} and {}" + tail).format)
number_error = partial(("Error: cannot convert {} to int" + tail).format)

def eval(expr):
    """Attempt to left-fold expression."""
    tokens = expr.strip().split()
    if not tokens:
        return None
    stack = list()
    for token in tokens:
        if token in ops:
            if not stack:
                print no_left_error(token, expr)
                return None
            elif stack[-1] in ops:
                print adj_ops_error(stack[-1], token, expr)
                return None
            # Push op onto stack
            stack.append(token)
        else:
            try:
                token = int(token)
            except ValueError:
                print number_error(token, expr)
                return None
            # Branch based on top of stack
            if stack:
                top = stack.pop()
                if top in ops:
                    # Try to get a left operand to go with top op
                    if not stack:
                        print no_left_error(top, expr)
                        return None
                    left = stack.pop()
                    if left in ops:
                        print adj_ops_error(left, token, expr)
                        return None
                    # Apply operator and push result onto stack
                    stack.append(ops[top](left, token))
                else:
                    print no_op_error(top, token, expr)
                    return None
            else:
                # If stack is empty push operand (seed operand)
                stack.append(token)
    assert len(stack) == 1
    return stack.pop()

if __name__ == '__main__':
    assert eval("1 + 1") == 2
    assert eval("2 * 4") == 8
    assert eval("10 / 2") == 5
    assert eval("2 * 10 + 2 / 2") == 11
    assert eval("-2 * 2 * 4") == -16
    # Calculator REPL
    res = "Type a space-separated expression"
    while res:
        print res
        res = eval(raw_input())
