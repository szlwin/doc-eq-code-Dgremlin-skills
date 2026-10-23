# Project philosophy: letting the design take part in program execution

[Back to documentation home](README.md)

Anyone who has built business systems has probably run into this: the requirements document says an order can be paid, yet a few constraints remain unwritten in the code; the flowchart has already been updated, but some endpoint still runs by the old rules. Only when something breaks does everyone start asking: which copy is authoritative right now?

These discrepancies usually come from the same rule being written down several times. The document writes it once, the endpoint checks it once, and a background job checks it again. Each copy looks reasonable on its own, yet when requirements change it is easy to miss one of them.

DEC wants to reduce this duplication. Put the data, the conditions, and the business flows into one structured design that people can read and programs can use. When a rule changes, change this design first, then update the affected pages, code, and tests. The order-payment demo in this repository is an attempt at this approach.

## Starting from a single order

Start with the [order-payment flowchart](../../demo/design-html/directory/order-payment.html). It describes a common business path: submit the order, start payment, receive the payment result, then handle success and failure separately.

What really needs to be spelled out here is usually the conditions between the arrows. Which orders are allowed to be paid? Is the user valid? After a failed result arrives, what should be recorded, and should the user be notified? These agreements determine what the program should do.

In the demo's [flow design](../../demo/order-payment-yaml/business-order-payment.yaml), before entering "payment in progress", "order payable" and "user valid" must be checked. After the payment result is received, the success or failure condition selects the branch; the failure branch schedules error recording, SMS notification, and an order status update.

This content is written in YAML. YAML is a text format, used here to hold the formal design; the canonical YAML mentioned in these documents is this design source that serves as the basis for changes. The generated HTML draws it as a flowchart so people can review it easily. While the application runs, the shared execution engine reads the design, checks conditions, selects branches, and invokes the corresponding actions. This engine is called the Runtime in the project.

As a result, the branches on the flowchart and the branches executed by the program trace back to the same definition. Someone taking over sees "send a notification after payment fails" and can keep tracing the corresponding action and implementation, without having to guess whether the diagram and the code are talking about the same thing.

## Separating "what it is" from "how to do it"

To understand this demo, start with two questions: **What state is this order in right now? What is allowed to happen next?**

"User valid", "order payable", and "payment result is failure" are all judgments about the current data. DEC uses Information to express such business facts. The same fact can be referenced from multiple places; for example, entering the payment flow requires checking whether the order is payable, and another business entry point can use the same judgment.

"Start payment after checking conditions" and "record the error after receiving a failed result" instead arrange how things happen. DEC uses Directory to organize these flow stages, connecting the conditions with the actions to execute.

The remaining names can also be understood by putting them back into this example:

| Name in the design | What it does in the order demo |
| --- | --- |
| Data / View | Describe the order, line items, user, and payment information, and which data a business process needs to bring together |
| Information | Express facts about what currently holds, such as "order payable" or "user valid" |
| RuleView | Organize concrete check, read, or write rules, for use by fact judgments or flow actions |
| Directory | Describe how the flow advances, when actions execute, and how branches or rollbacks are chosen |
| API | Define how the outside submits orders, starts payments, passes in results, and queries data |

The value of this split is that every judgment has a clear home. Later, when the conditions of "order payable" are adjusted, you can find its definition and references instead of searching for similar `if`s in every endpoint. Queries can also use the facts in the design to identify which stage an order is currently in. Full descriptions of each concept are in the [design model](design-model.md).

However, "what state is it in" cannot answer every question. The demo can query orders whose current payment succeeded, but current data alone cannot answer "did this order succeed in paying last week". A rollback changes the current data but does not erase what has already happened. To query history or do audits, events or business records still need to be stored separately.

## When one rule changes, which places must change

Suppose a requirement is added later: before starting payment, the order must be checked against the payment deadline. This is an extension example; the current demo does not implement this capability.

We must first define "timed out or not" clearly: when the clock starts, which time field is used, and whether an order can be placed again after a timeout. Then add the judgment to the design and have the payment-start flow reference it. If the existing rule capabilities cannot perform this check, the implementation must be extended as well.

After the design changes, regenerate the reading pages and the affected code, then verify the behavior within the deadline, beyond the deadline, and at the boundary moment. This way you can check along one line: does the design contain this requirement, does the flow use it, does the implementation support it, and do the tests cover it.

This is the practical meaning of "one design, used in many places". The HTML helps people understand the design, the generated code provides types and integration interfaces, and the Runtime executes by the design. Each has its own use, but business conditions should be changed in one and the same place. Changing only the text on a page, or adding a check only inside one endpoint, breaks this connection.

Sometimes external tools modify the XML; those changes can also be [merged back into YAML in a controlled way](xml-to-yaml-sync.md). After the merge, later artifacts are still generated from the YAML, preventing the two sides from evolving separately. The exact relationship between conversion and generation is covered in [YAML, XML and HTML](yaml-xml-html.md).

## Once the design is written clearly, what is left for the code to do

Take failure notification as an example: the design can say "send an SMS notification after payment failure", but how to connect to the SMS platform, how to handle timeouts, and whether a failed send should be retried are all engineering work.

The demo provides a concrete example of this: the SMS action first registers a post-commit callback, and only calls the sender after the database transaction commits successfully. The current sender only logs. This example shows the ordering of notification versus transaction, but it cannot prove that an SMS actually reached the user's phone.

Likewise, having "record payment error" in the design does not mean a production MongoDB is wired up; having "start payment" in the flowchart does not mean a real payment platform is connected either. The demo uses local stubs for these external capabilities; see the [order-payment example](order-payment-demo.md).

Business design and engineering implementation have to meet here: the design states when something happens and what result is needed; the code accesses real resources, manages transactions, and handles failures. Behavior the design does not describe needs added agreements; capabilities the implementation cannot yet deliver need an explicit gap. When an adapter is missing, a condition cannot be decided, or an execution error occurs, the most reliable move is to report failure or record a gap explicitly, not to keep going and call the result a success. For example, when a payment result cannot be recognized, it must not be treated as payment success by default; when a branch has not been wired up yet, it should stay in an unfinished state. The project's `DESIGN_GAP`, `UNRESOLVED`, and `ERROR` all remind whoever takes over: no certain conclusion can be drawn here yet.

## Hand this contract to the next person

We call this kind of design an "executable business contract" because it helps both sides state the agreement clearly and also takes part in the actual checks and execution.

The person responsible for the design must write the conditions, flows, and inputs/outputs down clearly, giving every node that needs tracing a stable Design ID. The person responsible for the implementation must find the code or runtime capability behind each of these nodes and use tests to show that they really work as agreed. The project's Implementation Binding is the manifest that records these correspondences.

For example, "update the order status after payment failure" is not done merely because the design says so and the code was generated. A real order must actually walk the failure path, and the status must be checked; if a write fails midway, you must also check whether incomplete data was left behind. Existing concurrency tests even let two requests submit the same order simultaneously and check the endpoint responses and the final database contents. These testing methods are covered in the [development and verification guide](development-and-verification.md).

With this layer of correspondence in place, whoever takes over can start from a business node and find the implementation and the tests. When Agents take part in development, they follow the same contract: what is implemented must have evidence, and what is unfinished stays marked as unfinished. When the design or the code changes, the affected tests must be rechecked; a report that passed last time cannot keep serving as the guarantee.

Tests and automated checks can reduce omissions, but whether the business requirements themselves are reasonable, and whether the scenarios have been thought through, still need human judgment. The value of this contract is giving the discussion a concrete anchor.

## When is this worth doing

This approach requires upfront investment. Judgments that used to be written directly into code now have to be thought through first as data, facts, or flow; the team also has to learn the models and the tools. When rules change, the design, the adapted code, and the tests must be maintained together.

The application also depends on how the Runtime executes and on the generator's capabilities. Requirements that cannot be expressed may require extending the framework or writing adapters. Consistency across payment, messaging, and databases, along with retries, compensation, and history, also need separate designs. Adopting DEC does not make this work disappear.

If the business will be maintained for many years, the same rule is used by many entry points, or the project changes hands repeatedly, this investment tends to pay off: fewer duplicate judgments to maintain, an easier time knowing what a change affects, and a path from design to implementation and tests when taking over.

If it is just a short-lived tool with very few rules, writing clear code with good tests directly may be simpler. Whether to adopt DEC can be judged from the problems in front of you: has the team already spent a lot of time reconciling documents with code, and do errors often come from the same rule having multiple implementations?

To keep exploring, open the [order-payment demo](order-payment-demo.md) first and walk the success, failure, and rollback paths against the flowchart; then read the [overall architecture](architecture.md) to see how the design is read and executed by the program.

## A few terms you will encounter next

| Term | How to understand it in this project |
| --- | --- |
| canonical YAML | The design source that serves as the basis for changes; XML and HTML are both derived from it |
| Runtime | The shared execution engine that reads the design, evaluates conditions, and advances flows |
| Design ID | The stable identity of a design node, helping code, tests, and documentation point to the same node |
| Binding | Records which code, Runtime capability, or test implements a given design node |
| DESIGN_GAP | An explicit gap still present in the design or implementation; it must not be quietly treated as done |
