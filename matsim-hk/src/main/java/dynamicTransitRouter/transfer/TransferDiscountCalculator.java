package dynamicTransitRouter.transfer;

import java.math.BigDecimal;

import org.matsim.api.core.v01.Id;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import dynamicTransitRouter.RouteHelper;

public interface TransferDiscountCalculator {
	
	/**
	 * Given the from route and to route, determine the interchange discount, which is a non-negative number.<br>
	 * <strike>A negative return value means there is a rebate.</strike> <i>not enabled</i><br><br>
	 * 
	 * Double is the return type for efficiency, for exact discount, go for getExactInterchangeDiscount.
	 * </p><br>
	 */
	public double getInterchangeDiscount(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId, 
			Id<TransitRoute> fromTransitRouteId, Id<TransitRoute> toTransitRouteId, 
			String fromMode, String toMode, double lastStartTime, double lastEndTime, double thisAboardTime,
			double lastFare, double thisFare);
	
	/**
	 * Given the from route and to route, determine the interchange discount, which is a non-negative number.<br>
	 * <strike>A negative return value means there is a rebate.</strike> <i>not enabled</i><br><br>
	 * 
	 * BigDecimal is the return type for precision, for efficiency, go for getInterchangeDiscount.
	 * </p><br>
	 */
	public BigDecimal getExactInterchangeDiscount(Id<TransitLine> fromTransitLineId, Id<TransitLine> toTransitLineId, 
			Id<TransitRoute> fromTransitRouteId, Id<TransitRoute> toTransitRouteId, 
			String fromMode, String toMode, double lastStartTime, double lastEndTime, double thisAboardTime,
			double lastFare, double thisFare);
}
