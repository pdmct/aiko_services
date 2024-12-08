**Diagram Documentation for ********`Graph`******** and ********`Node`******** Classes**

### Overview

This document provides a set of diagrams to help understand the structure and operation of the `Graph` and `Node` classes. These diagrams cover:

1. **Class Relationships** - Depicts the relationships and properties of the `Graph` and `Node` classes.
2. **Example Usage Flow** - Illustrates a step-by-step process of using the `Graph` and `Node` classes based on the provided usage example.
3. **Graph Traversal** - Demonstrates how the `traverse` function operates on a structured graph definition.

---

### 1. Class Relationships

#### UML Diagram:
```html
<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">
  <rect x="10" y="10" width="180" height="100" fill="lightgray" stroke="black" />
  <text x="20" y="30" font-family="Arial" font-size="14">Graph</text>
  <text x="20" y="50" font-family="Arial" font-size="12">- _graph: OrderedDict</text>
  <text x="20" y="70" font-family="Arial" font-size="12">- _head_nodes: OrderedDict</text>
  <text x="20" y="90" font-family="Arial" font-size="12">+ add(node: Node)</text>

  <rect x="210" y="10" width="180" height="100" fill="lightgray" stroke="black" />
  <text x="220" y="30" font-family="Arial" font-size="14">Node</text>
  <text x="220" y="50" font-family="Arial" font-size="12">- _name: str</text>
  <text x="220" y="70" font-family="Arial" font-size="12">- _element: Any</text>
  <text x="220" y="90" font-family="Arial" font-size="12">- _successors: OrderedDict</text>

  <line x1="190" y1="60" x2="210" y2="60" stroke="black" />
  <text x="195" y="55" font-family="Arial" font-size="12">1</text>
  <text x="195" y="65" font-family="Arial" font-size="12">*</text>
</svg>
```

---

### 2. Example Usage Flow

#### Code:
```python
from aiko_services.main.utilities import *
graph = Graph()
node_a = Node("a")
node_b = Node("b")
node_a.add("b")
graph.add(node_a)
graph.add(node_b)
print(graph.nodes())
```

#### Flow Diagram:
```html
<svg xmlns="http://www.w3.org/2000/svg" width="400" height="400" viewBox="0 0 400 400">
  <rect x="100" y="10" width="200" height="50" fill="lightblue" stroke="black" />
  <text x="120" y="40" font-family="Arial" font-size="14">Create Graph</text>

  <line x1="200" y1="60" x2="200" y2="90" stroke="black" />

  <rect x="100" y="90" width="200" height="50" fill="lightblue" stroke="black" />
  <text x="120" y="120" font-family="Arial" font-size="14">Create Node "a"</text>

  <line x1="200" y1="140" x2="200" y2="170" stroke="black" />

  <rect x="100" y="170" width="200" height="50" fill="lightblue" stroke="black" />
  <text x="120" y="200" font-family="Arial" font-size="14">Add "b" as successor</text>
  <text x="120" y="215" font-family="Arial" font-size="14">of "a"</text>

  <line x1="200" y1="220" x2="200" y2="250" stroke="black" />

  <rect x="100" y="250" width="200" height="50" fill="lightblue" stroke="black" />
  <text x="120" y="280" font-family="Arial" font-size="14">Add "a" and "b"</text>
  <text x="120" y="295" font-family="Arial" font-size="14">to graph</text>

  <line x1="200" y1="300" x2="200" y2="330" stroke="black" />

  <rect x="100" y="330" width="200" height="50" fill="lightblue" stroke="black" />
  <text x="120" y="360" font-family="Arial" font-size="14">Display all graph nodes</text>
</svg>
```

---

### 3. Graph Traversal

#### Example Definition:
```python
heads, successors = graph.traverse([
  "(a (b d) (c d))"
])
```

#### Traversal Process:
1. Parse the string `"(a (b d) (c d))"` into a tree-like structure.
2. Identify `a` as the head node.
3. Add `b` and `c` as successors to `a`.
4. Add `d` as successors to both `b` and `c`.

#### Diagram:
```svg
<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300" viewBox="0 0 400 300">
  <circle cx="200" cy="50" r="20" fill="lightgreen" stroke="black" />
  <text x="195" y="55" font-family="Arial" font-size="14">a</text>

  <circle cx="150" cy="150" r="20" fill="lightgreen" stroke="black" />
  <text x="145" y="155" font-family="Arial" font-size="14">b</text>

  <circle cx="250" cy="150" r="20" fill="lightgreen" stroke="black" />
  <text x="245" y="155" font-family="Arial" font-size="14">c</text>

  <circle cx="200" cy="250" r="20" fill="lightgreen" stroke="black" />
  <text x="195" y="255" font-family="Arial" font-size="14">d</text>

  <line x1="200" y1="70" x2="150" y2="130" stroke="black" />
  <line x1="200" y1="70" x2="250" y2="130" stroke="black" />
  <line x1="150" y1="170" x2="200" y2="230" stroke="black" />
  <line x1="250" y1="170" x2="200" y2="230" stroke="black" />
</svg>
```

#### Data Structure:
- **Heads**: `{ "a": Node("a") }`
- **Successors**:
  ```python
  {
    "a": {"b": "b", "c": "c"},
    "b": {"d": "d"},
    "c": {"d": "d"}
  }
  ```

---

These diagrams and explanations should help in visualizing the functionality and relationships within the `Graph` and `Node` classes.

