import { describe, expect, it } from 'vitest'
import { accuracyRing } from './mapEngine'

/**
 * The accuracy circle is the one part of drawing a map that is arithmetic rather than graphics, and
 * it is the part that can be quietly wrong: a radius drawn in the wrong units still looks like a
 * circle, and would present a hundred-metre uncertainty as a confident dot.
 */
describe('the accuracy ring', () => {
  const centre = { kind: 'courier' as const, latitude: 51.5074, longitude: -0.1278, accuracyMetres: 100 }

  it('closes on itself so the polygon is a ring rather than an arc', () => {
    const ring = accuracyRing(centre, 8)

    expect(ring).toHaveLength(9)
    expect(ring[0]).toEqual(ring[8])
  })

  it('is the reported radius wide on the ground, in metres and not in pixels', () => {
    const ring = accuracyRing(centre, 4)
    const [, north] = ring[1]
    const [east] = ring[0]

    // 100 m of latitude is a fixed 0.000899°; the same distance of longitude is wider the further
    // from the equator you are, which is exactly the correction a naive circle forgets.
    expect(north - centre.latitude).toBeCloseTo(0.000899, 6)
    expect(east - centre.longitude).toBeCloseTo(0.001445, 6)
  })

  it('collapses to the centre when the reading carries no accuracy at all', () => {
    const ring = accuracyRing({ kind: 'handoff', latitude: 51.5074, longitude: -0.1278 }, 4)

    for (const [longitude, latitude] of ring) {
      expect(longitude).toBeCloseTo(-0.1278, 10)
      expect(latitude).toBeCloseTo(51.5074, 10)
    }
  })
})
