# MatrixNet: The Operator's Console

MatrixNet is a Java-based network simulation project developed for a Data Structures and Algorithms course. The program reads commands from an input file, manages a network of hosts and backdoor connections, and writes the results to an output file.

The project supports host creation, backdoor linking and sealing, route tracing with bandwidth and firewall constraints, connectivity checks, breach simulations, and network topology reports. A custom hash map and graph-based algorithms are used to handle large inputs efficiently.

## Features

* Spawn hosts with clearance levels
* Link and seal/unseal backdoor connections
* Find optimal routes with latency, bandwidth, and firewall constraints
* Scan network connectivity
* Simulate host and backdoor breaches
* Detect bridges, articulation points, and cycles
* Generate Oracle network reports
* File-based input and output processing

## Technologies

* Java
* Object-Oriented Programming
* Graph Algorithms
* Custom Hash Map
* Data Structures and Algorithms
* File I/O

## Project Structure

```text
src/
  Main.java
  Core.java
  Host.java
  Backdoor.java
  MyHashMap.java
```

## How to Run

Compile the source files:

```bash
javac *.java
```

Run the program:

```bash
java Main <input_file> <output_file>
```

Example:

```bash
java Main input.txt output.txt
```

## Notes

This project was developed as part of a university Data Structures and Algorithms course. Assignment materials and official test cases are not included in this repository.
