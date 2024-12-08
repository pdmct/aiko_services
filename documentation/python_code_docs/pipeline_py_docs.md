# Pipeline Management Documentation

## Overview
This documentation provides a comprehensive understanding of the `aiko_pipeline` management script, which facilitates creating, managing, and destroying pipelines consisting of pipeline elements (actors or services).

## Usage

### Command Line Interface (CLI)

#### Creating a Pipeline
```bash
aiko_pipeline create [--name $PIPELINE_NAME] $DEFINITION
```
Example:
```bash
aiko_pipeline create pipeline_definition.json --log_level debug --stream_id 1 --frame_data "(argument_name: argument_value ...)"
```

#### Destroying a Pipeline
```bash
aiko_pipeline destroy $PIPELINE_NAME
```

### MQTT Integration
Use MQTT to interact with pipelines dynamically.

**Create Stream:**
```bash
mosquitto_pub -h $HOST -t $TOPIC -m "(create_stream 1)"
```

**Process Frame:**
```bash
mosquitto_pub -h $HOST -t $TOPIC -m "(process_frame (stream_id: 1) (a: 0))"
```

**Destroy Stream:**
```bash
mosquitto_pub -h $HOST -t $TOPIC -m "(destroy_stream 1)"
```

### Pipeline Definition
A pipeline definition specifies the graph of pipeline elements. Example definitions:

**Simple Graph:**
```json
"graph": [
  "(PE_0 PE_1)"
]
```

**Complex Graph:**
```json
"graph": [
  "(PE_0 (PE_1 (PE_3 PE_5)) (PE_2 (PE_4 PE_5)))"
]
```

**Mapping Function Arguments:**
```json
"graph": [
  "(PE_0 (PE_1 PE_3 (a: x)) (PE_2 PE_3 (b: y)))"
]
```

## Key Concepts

### Relationship to Actors
Pipelines and PipelineElements in the `aiko_pipeline` framework are closely tied to the concept of **Actors**, which represent independent entities capable of processing data and interacting with other actors via defined protocols.

- **Pipeline as an Actor:**
  The `Pipeline` class is derived from the Actor abstraction, making it a first-class entity that interacts with other actors in the system. It manages the lifecycle of streams and coordinates the execution of PipelineElements.

- **PipelineElement as an Actor:**
  Each `PipelineElement` is an independent actor that processes data frames and communicates with other elements. By adhering to the Actor interface, PipelineElements can function either locally or as remote services, leveraging Actor discovery and messaging protocols.

### Integration with Actor System
- **Protocols:** Pipelines and elements utilize predefined protocols (e.g., `PROTOCOL_PIPELINE`) for communication.
- **Discovery:** Remote elements are dynamically discovered and proxied using the Actor discovery mechanism.
- **Lifecycle Management:** Actors manage their own lifecycle states, such as `ready` or `absent`, enabling robust and dynamic pipeline configurations.


### Pipeline Elements
A `PipelineElement` represents an actor or service within the pipeline. Each element has inputs, outputs, and parameters.

### Deployment Types
Pipeline elements can be deployed:
- **Locally:** Using a specific Python class and module.
- **Remotely:** By specifying a service filter for discovery.

### Stream Management
Streams manage data flow through the pipeline. Operations include:
- **create_stream()**: Initializes a stream.
- **process_frame()**: Processes a data frame.
- **destroy_stream()**: Cleans up the stream.

## Pipeline Class
The `Pipeline` class is the core abstraction for managing pipelines. It provides methods to:
- Create and destroy streams.
- Process frames and handle stream states.
- Parse pipeline definitions from JSON files and validate them.

#### Key Methods
- `create_stream()`: Initializes a new stream for data processing.
- `destroy_stream()`: Terminates an existing stream and releases resources.
- `process_frame()`: Processes an individual frame of data in the pipeline.
- `parse_pipeline_definition()`: Parses and validates a pipeline definition file.

The `PipelineImpl` class is the concrete implementation that handles the underlying operations, including:
- Managing graph-based relationships between pipeline elements.
- Handling both local and remote deployment scenarios.
- Interacting with MQTT for dynamic pipeline management.

## PipelineElement Class
The `PipelineElement` class represents the individual actors or services within the pipeline. It is an abstract base class defining the following methods:
- `process_frame()`: Processes a data frame received from a stream.
- `start_stream()`: Starts a new stream for the element.
- `stop_stream()`: Stops a stream managed by the element.
- `get_parameter()`: Retrieves a configuration parameter for the element.

#### Implementation
The `PipelineElementImpl` class provides a concrete implementation with features such as:
- Dynamic parameter management and logging.
- Support for distributed processing and frame creation.
- Integration with both local and remote pipelines.

### Deployment Scenarios
- **Local Elements:** Defined by a Python class and module. Ideal for tightly coupled operations.
- **Remote Elements:** Use MQTT for discovery and communication, enabling distributed pipeline setups.

## CLI Options

### `create` Command Options
| Option              | Description                                      |
|---------------------|--------------------------------------------------|
| `--name`            | Name of the pipeline.                           |
| `--graph_path`      | Specify the graph path for the pipeline.         |
| `--parameters`      | Define stream parameters.                        |
| `--stream_id`       | Identifier for the stream.                       |
| `--frame_id`        | Identifier for the frame.                        |
| `--frame_data`      | Data to process in the frame.                    |
| `--log_level`       | Logging level (e.g., debug, info).               |
| `--windows`         | Enable sliding window protocol.                  |
| `--exit_message`    | Display a message upon pipeline exit.            |

### `destroy` Command Options
| Option              | Description                                      |
|---------------------|--------------------------------------------------|
| `name`              | Name of the pipeline to destroy.                |

## Internal Components

### Pipeline Classes
- **Pipeline:** Base class for managing pipeline operations.
- **PipelineImpl:** Concrete implementation with dynamic graph management.
- **PipelineElement:** Represents an individual actor or service.
- **PipelineElementImpl:** Concrete implementation for processing and managing streams.

### Validation
Pipeline definitions are validated against an Avro schema to ensure correctness.

## To-Do
- Add CLI commands for showing and setting parameters.
- Enhance support for remote elements and dynamic proxies.

## Example Diagram
Below is a visual representation of a sample pipeline graph.

### Pipeline Graph
The diagram represents a hierarchical structure where nodes are pipeline elements, and edges denote data flow.

```mermaid
graph TD
  PE_0 -->|Output A| PE_1
  PE_0 -->|Output B| PE_2
  PE_1 --> PE_3
  PE_2 --> PE_3
  PE_3 --> PE_5
```

## Resources
- [Avro Specification](https://avro.apache.org/docs/1.9.1/spec.html)
- Python libraries for handling Avro:
  - [python-avro-json-serializer](https://github.com/linkedin/python-avro-json-serializer)
  - [python-schema-registry-client](https://marcosschroh.github.io/python-schema-registry-client)

## Diagram Embed
The following PNG images visually represent the system:

### Sample Pipeline Graph:
![Pipeline Graph](pipeline_graph.png)

### Class Relationships:
![Class Relationships](class_relationships.png)


## Conclusion
This documentation and the accompanying diagrams provide a foundation for understanding and managing the `aiko_pipeline` system. For more advanced usage, refer to the source code and resources listed above.

