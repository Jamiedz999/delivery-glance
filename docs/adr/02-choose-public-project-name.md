# ADR 02 — The product is called Delivery Glance

## The question

What should the product, the repository and the portfolio entry be called?

## What we decided

**Delivery Glance**, described as *"Real-time delivery tracking for the last mile."* The repository
slug is `delivery-glance`.

## Why

`Delivery` says what the product is about without anyone having to think. `Glance` says what the
Recipient gets: status, location, and what happens next, all readable in one look.

`Delivery Tracker` was the obvious alternative and was rejected. A search found existing projects
with that exact name doing substantially the same job on the same stack — Spring Boot plus React —
which would make this one look derivative.

## What is built

The name is used everywhere: repository, package names (`com.deliveryglance`), the demo accounts, and
the deployed site.
