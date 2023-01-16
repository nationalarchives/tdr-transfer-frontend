package configuration

import play.api.http.{DefaultHttpFilters, EnabledFilters}
import play.filters.gzip.GzipFilter

import javax.inject.Inject

class CustomFilters @Inject() (defaultFilters: EnabledFilters, gzip: GzipFilter, log: AccessLoggingFilter) extends DefaultHttpFilters(defaultFilters.filters :+ gzip :+ log: _*)
