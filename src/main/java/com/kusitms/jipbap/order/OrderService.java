package com.kusitms.jipbap.order;

import com.kusitms.jipbap.food.Food;
import com.kusitms.jipbap.food.FoodRepository;
import com.kusitms.jipbap.food.exception.FoodNotExistsException;
import com.kusitms.jipbap.order.dto.OrderDto;
import com.kusitms.jipbap.order.dto.OrderFoodRequestDto;
import com.kusitms.jipbap.order.exception.OrderNotExistsByOrderStatusException;
import com.kusitms.jipbap.order.exception.OrderNotFoundException;
import com.kusitms.jipbap.order.exception.UnauthorizedAccessException;
import com.kusitms.jipbap.user.User;
import com.kusitms.jipbap.user.UserRepository;
import com.kusitms.jipbap.user.exception.UserNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final FoodRepository foodRepository;

    public OrderDto orderFood(OrderFoodRequestDto dto) {
        User user = userRepository.findById(dto.getUser()).orElseThrow(()-> new UserNotFoundException("유저 정보가 존재하지 않습니다."));
        Food food = foodRepository.findById(dto.getFood()).orElseThrow(()-> new FoodNotExistsException("해당 음식은 유효하지 않습니다."));

        Order order = orderRepository.save(
                Order.builder()
                        .user(user)
                        .food(food)
                        .orderCount(dto.getOrderCount())
                        .totalPrice(dto.getTotalPrice())
                        .regionId(user.getGlobalRegion().getId())
                        .status(OrderStatus.PENDING)
                        .build()
        );
        return new OrderDto(order);
    }

    public OrderDto getOrderDetail(Long orderId) {
        Order order = orderRepository.findById(orderId).
                orElseThrow(()-> new OrderNotFoundException("해당 주문" + orderId + "는 유효하지 않습니다."));
        return new OrderDto(order);
    }

    public List<OrderDto> getStoreOrderHistoryByOrderStatus(Long storeId, String orderStatus) {
        OrderStatus status = OrderStatus.fromString(orderStatus);
        List<Order> orders = orderRepository.findByFood_Store_IdAndStatus(storeId, status)
                .orElseThrow(() -> new OrderNotExistsByOrderStatusException("해당 가게의 주문상태에 따른 주문 내역이 존재하지 않습니다."));
        if (orders.isEmpty()) {
            throw new OrderNotExistsByOrderStatusException("해당 가게의 주문상태에 따른 주문 내역이 존재하지 않습니다.");
        }

        return orders.stream().map(OrderDto::new).collect(Collectors.toList());
    }

    @Transactional
    public void processOrder(Long orderId, String status) {
        OrderStatus newStatus = OrderStatus.fromString(status);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다. orderId: " + orderId));

        // 판매자의 권한 확인 (현재 사용자 정보와 주문내역의 판매자 정보가 같은지 확인)
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String sellerUsername = userDetails.getUsername();

        User seller = userRepository.findByUsername(sellerUsername)
                .orElseThrow(() -> new UserNotFoundException("판매자를 찾을 수 없습니다."));

        if (!seller.getId().equals(order.getFood().getStore().getOwner().getId())) {
            throw new UnauthorizedAccessException("주문 상태를 변경할 권한이 없습니다.");
        }

        order.setStatus(newStatus); // 주문 상태 변경
        // 알림 등 로직 추가 가능

        orderRepository.save(order);
    }

}