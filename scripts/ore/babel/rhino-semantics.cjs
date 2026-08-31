const t = require('@babel/types');

function isCharCodeAtCall(node) {
    if (!t.isCallExpression(node) || !t.isMemberExpression(node.callee)) return false;
    const property = node.callee.property;
    return node.callee.computed
        ? t.isStringLiteral(property, { value: 'charCodeAt' })
        : t.isIdentifier(property, { name: 'charCodeAt' });
}

function charAtCall(node) {
    const call = t.cloneNode(node, true);
    call.callee.property = t.identifier('charAt');
    call.callee.computed = false;
    return call;
}

function normalizeCharCodeComparison(path) {
    const leftMatches = isCharCodeAtCall(path.node.left) && t.isNumericLiteral(path.node.right);
    const rightMatches = t.isNumericLiteral(path.node.left) && isCharCodeAtCall(path.node.right);
    if (!leftMatches && !rightMatches) return;
    if (leftMatches) {
        path.node.left = charAtCall(path.node.left);
        path.node.right = t.stringLiteral(String.fromCharCode(path.node.right.value));
    } else {
        path.node.left = t.stringLiteral(String.fromCharCode(path.node.left.value));
        path.node.right = charAtCall(path.node.right);
    }
    if (path.node.operator === '===') path.node.operator = '==';
    if (path.node.operator === '!==') path.node.operator = '!=';
}

function getScopeBodyPath(scopePath) {
    if (scopePath.isProgram()) return scopePath;
    const bodyPath = scopePath.get('body');
    return bodyPath.isBlockStatement() ? bodyPath : null;
}

function collectVarDeclarations(scopePath) {
    const declarations = [];
    scopePath.traverse({
        Function(path) {
            if (path !== scopePath) path.skip();
        },
        StaticBlock(path) {
            path.skip();
        },
        VariableDeclaration(path) {
            if (path.node.kind === 'var') declarations.push(path);
        }
    });
    return declarations;
}

function assignmentExpressions(node) {
    return node.declarations
        .filter(declarator => declarator.init !== null)
        .map(declarator => t.assignmentExpression(
            '=',
            t.cloneNode(declarator.id, true),
            t.cloneNode(declarator.init, true)
        ));
}

function replaceDeclaration(path) {
    const parentPath = path.parentPath;
    const assignments = assignmentExpressions(path.node);

    if (parentPath.isForStatement() && path.parentKey === 'init') {
        path.replaceWith(
            assignments.length === 0
                ? null
                : assignments.length === 1
                    ? assignments[0]
                    : t.sequenceExpression(assignments)
        );
        return;
    }

    if ((parentPath.isForInStatement() || parentPath.isForOfStatement())
        && path.parentKey === 'left') {
        path.replaceWith(t.cloneNode(path.node.declarations[0].id, true));
        return;
    }

    const statements = assignments.map(assignment => t.expressionStatement(assignment));
    if (statements.length === 0) {
        path.remove();
    } else {
        path.replaceWithMultiple(statements);
    }
}

function transformScope(scopePath) {
    const bodyPath = getScopeBodyPath(scopePath);
    if (bodyPath === null) return;

    const declarations = collectVarDeclarations(scopePath);
    if (declarations.length === 0) return;

    const hoistedDeclarations = declarations.flatMap(path => path.node.declarations.map(
        declarator => t.variableDeclarator(t.cloneNode(declarator.id, true), null)
    ));

    for (const declaration of declarations) replaceDeclaration(declaration);

    bodyPath.unshiftContainer('body', t.variableDeclaration('var', hoistedDeclarations));
}

module.exports = function rhinoSemanticsPlugin(api) {
    api.assertVersion(7);

    return {
        name: 'rhino-semantics',
        visitor: {
            BinaryExpression: normalizeCharCodeComparison,
            Program: {
                exit(path) {
                    transformScope(path);
                }
            },
            Function: {
                exit(path) {
                    transformScope(path);
                }
            }
        }
    };
};
