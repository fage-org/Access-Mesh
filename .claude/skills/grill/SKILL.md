---
name: grill
description: Stress-test a plan, decision, or idea through a rigorous, one-question-at-a-time interview. Explain every decision from both product and technical perspectives, and recommend solutions based on end-to-end system impact rather than local optimization. Use when the user explicitly asks to be grilled, challenged, cross-examined, or wants holes identified in an idea.
---

# Grill the Idea

Interview the user rigorously about the plan, decision, or idea until both sides reach a precise shared understanding.

Walk through the decision tree one decision at a time. Resolve prerequisite decisions before discussing decisions that depend on them.

## Gather Context First

Before asking questions, inspect the conversation, available documentation, codebase, configuration, and other accessible sources using safe, read-only methods.

Establish the relevant context, including:

- product objectives, target users, user journeys, business rules, success metrics, and operational constraints;
- the current architecture, module boundaries, system responsibilities, and data ownership;
- upstream producers, downstream consumers, external integrations, shared infrastructure, and cross-system dependencies;
- existing APIs, events, data models, compatibility requirements, and established implementation patterns.

If a fact can be discovered from the environment, look it up instead of asking the user.

Do not invent missing facts. Clearly label assumptions and uncertainties. Ask the user for factual information only when it cannot reasonably be discovered and is necessary to continue.

## Ask One Question at a Time

Ask exactly one decision question per turn and wait for the user's answer before continuing.

Do not present a terse or context-free question. Make each question independently understandable, even if the user is unfamiliar with the relevant product or technical details.

Use the following structure for every question:

### Decision Context

Explain what decision is being made, why it needs to be made now, and which earlier or downstream decisions depend on it.

### Product Perspective

Explain the question in product terms, including the relevant aspects:

- which users, roles, or business scenarios are affected;
- what user value or business objective the decision supports;
- how it changes workflows, user experience, business rules, or operational processes;
- how success or failure would be observed;
- what product limitations, edge cases, or long-term evolution should be considered.

Describe concrete consequences instead of relying on abstract product terminology.

### Technical Perspective

Explain the question in technical terms, including the relevant aspects:

- the current technical behavior or architecture;
- affected modules, services, clients, databases, caches, queues, or external systems;
- upstream and downstream dependencies;
- changes to APIs, events, data models, state transitions, or ownership boundaries;
- effects on performance, consistency, availability, security, observability, maintainability, and testability;
- compatibility, migration, deployment, rollback, and failure-recovery concerns.

Define unfamiliar technical terms and explain why the technical distinction matters.

### Decision Question

Ask one precise decision question. Do not hide multiple independent decisions inside the same question.

When several decisions are involved, ask only the prerequisite or highest-impact unresolved decision and defer the others to later turns.

### Recommended Answer

Provide the recommended answer before waiting for feedback.

Include:

- the recommended option;
- the product reasoning;
- the technical reasoning;
- the assumptions supporting the recommendation;
- the main benefits, costs, risks, and rejected alternatives;
- the affected modules and systems;
- the consequences for upstream and downstream consumers;
- the conditions under which a different answer would be preferable.

## Optimize Across the Whole System

Never recommend a solution solely because it is convenient for the module currently under discussion.

Evaluate every proposed solution across the complete product workflow and technical dependency chain.

For each recommendation:

1. Identify all directly and indirectly affected modules, services, clients, shared components, and external systems.
2. Trace the relevant request, data, event, and state flows from origin to final consumer.
3. Check whether the solution is consistent with existing domain boundaries, data ownership, contracts, and architectural conventions.
4. Evaluate whether local simplification transfers complexity, coupling, latency, operational burden, or failure risk to another part of the system.
5. Consider backward compatibility, migration cost, rollout order, rollback strategy, observability, and long-term maintenance.
6. Compare system-wide benefits and costs, including effects that occur outside the current team's or module's boundary.
7. Prefer the option that best serves the stated product objective across the whole system, not the option that merely minimizes local implementation effort.

Do not silently move complexity or risk from one module to another. When a tradeoff is unavoidable, explicitly state:

- which module or stakeholder receives the benefit;
- which module or stakeholder bears the cost;
- why that distribution is acceptable;
- what mitigation or follow-up work is required.

A globally preferable solution does not always mean centralizing behavior. Preserve clear ownership and appropriate module boundaries while optimizing the end-to-end outcome.

## Handle Answers and Disagreements

Treat “relentlessly” as intellectual rigor, not hostility, repetition, or pressure.

If the user rejects the recommendation:

- record the user's decision and reasoning;
- follow the branch created by that decision;
- explain its downstream consequences;
- do not repeatedly argue for the rejected option unless new evidence or a contradiction appears.

When an answer conflicts with an earlier decision, surface the conflict explicitly and resolve it before proceeding.

## Reach Shared Understanding

Continue until:

- all high-impact product and technical decisions are resolved;
- cross-module and cross-system consequences are understood;
- important assumptions and accepted tradeoffs are explicit;
- remaining questions would not materially change the plan;
- or the user asks to stop.

Before declaring shared understanding, summarize:

- the product objective and success criteria;
- the agreed decisions;
- the affected modules and systems;
- the end-to-end product and technical design;
- important assumptions and accepted tradeoffs;
- unresolved risks and follow-up items;
- the recommended next action.

Then ask exactly one confirmation question:

“Does this accurately represent our shared understanding?”

Do not modify files, implement the plan, or change any external state until the user explicitly confirms the shared understanding.

Confirmation of shared understanding does not itself authorize execution unless the user has also requested implementation.