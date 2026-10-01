/**
 * Short plain-language note about the service.
 *
 * Rendered after the bus results so the live list stays above the fold on a
 * phone, while still being real, visible text in the rendered page (not
 * hidden) for readers and for search engines that render the app. Kept to a
 * few sentences and limited to what the app actually does.
 */
export default function SeoIntro() {
  return (
    <section className="seo-intro" aria-labelledby="seo-intro-heading">
      <h2 id="seo-intro-heading">KA Bus Tracking</h2>
      <p>
        KA Bus Tracking is live bus tracking for Karnataka. Search for a bus by entering your
        starting stop in <strong>From</strong> and your destination in <strong>To</strong>, then
        press Search to see the routes and stops it serves and where the bus is right now.
      </p>
    </section>
  )
}
